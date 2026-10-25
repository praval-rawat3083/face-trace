package facetrace.app;

import facetrace.match.FaceMatcher;
import facetrace.match.MatchResult;
import facetrace.vision.FaceEmbedder;
import facetrace.vision.LbpEmbedder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Measures LBP matching on the local ORL dataset without changing the database. */
public final class Benchmark {
    private static final int SUBJECT_COUNT = 40;
    private static final int IMAGE_COUNT = 10;
    private static final int TOP_K = 3;

    /** Keeps this benchmark command from being created as an object. */
    private Benchmark() { }

    /** Runs ten folds, prints the measured scores, and saves the same report to a text file. */
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: Benchmark <dataset-folder> <threshold-0-to-1> [results.txt]");
            return;
        }
        Path datasetRoot = Path.of(args[0]).toAbsolutePath().normalize();
        double threshold = Double.parseDouble(args[1]);
        FaceMatcher matcher = new FaceMatcher(threshold);
        Path output = args.length == 3 ? Path.of(args[2]) : Path.of("benchmark-results.txt");
        output = output.toAbsolutePath().normalize();

        FaceEmbedder embedder = new LbpEmbedder();
        List<Subject> subjects = loadSubjects(datasetRoot, embedder);
        int correctTop1 = 0;
        int correctTop3 = 0;
        int searches = 0;
        long searchNanos = 0;

        // Each round holds out the same image number from every subject and enrolls the other nine.
        for (int heldOut = 0; heldOut < IMAGE_COUNT; heldOut++) {
            Map<String, float[]> gallery = new LinkedHashMap<>();
            for (Subject subject : subjects) {
                gallery.put(subject.caseCode(), averageOtherImages(subject.embeddings(), heldOut));
            }
            for (Subject subject : subjects) {
                long start = System.nanoTime();
                float[] probe = embedder.embed(subject.images().get(heldOut));
                List<MatchResult> matches = matcher.rankTopK(probe, gallery, TOP_K);
                searchNanos += System.nanoTime() - start;
                searches++;
                if (!matches.isEmpty() && matches.get(0).caseCode().equals(subject.caseCode())) {
                    correctTop1++;
                }
                if (matches.stream().anyMatch(match -> match.caseCode().equals(subject.caseCode()))) {
                    correctTop3++;
                }
            }
        }

        String report = buildReport(threshold, searches, correctTop1, correctTop3, searchNanos);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, report + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("Saved report to: " + output);
    }

    /** Loads the 40 numbered folders and checks that each has images 1.pgm through 10.pgm. */
    private static List<Subject> loadSubjects(Path root, FaceEmbedder embedder) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Dataset folder does not exist: " + root);
        }
        List<Path> folders;
        try (Stream<Path> children = Files.list(root)) {
            folders = children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("s[0-9]+"))
                    .sorted(Comparator.comparingInt(Benchmark::subjectNumber))
                    .toList();
        }
        if (folders.size() != SUBJECT_COUNT) {
            throw new IOException("Expected 40 subject folders named s1 through s40; found " + folders.size());
        }

        List<Subject> subjects = new ArrayList<>();
        for (int index = 0; index < folders.size(); index++) {
            Path folder = folders.get(index);
            int number = subjectNumber(folder);
            if (number != index + 1) {
                throw new IOException("Subject folders must be numbered s1 through s40");
            }
            List<Path> imagePaths = imageFiles(folder);
            if (imagePaths.size() != IMAGE_COUNT) {
                throw new IOException(folder + " must contain exactly 10 numbered .pgm images");
            }
            List<BufferedImage> images = new ArrayList<>();
            List<float[]> vectors = new ArrayList<>();
            for (int imageIndex = 0; imageIndex < IMAGE_COUNT; imageIndex++) {
                Path imagePath = imagePaths.get(imageIndex);
                if (imageNumber(imagePath) != imageIndex + 1) {
                    throw new IOException(folder + " must contain images 1.pgm through 10.pgm");
                }
                BufferedImage image = Phase2Demo.readPgm(imagePath);
                images.add(image);
                vectors.add(embedder.embed(image));
            }
            String label = String.format(Locale.ROOT, "Subject-%03d", number);
            subjects.add(new Subject(label, List.copyOf(images), List.copyOf(vectors)));
        }
        return List.copyOf(subjects);
    }

    /** Lists numbered PGM files in numeric order rather than alphabetic order. */
    private static List<Path> imageFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgm"))
                    .sorted(Comparator.comparingInt(Benchmark::imageNumber))
                    .toList();
        }
    }

    /** Reads the number from a folder named s12. */
    private static int subjectNumber(Path folder) {
        return Integer.parseInt(folder.getFileName().toString().substring(1));
    }

    /** Reads the number from a file named 7.pgm. */
    private static int imageNumber(Path image) {
        String name = image.getFileName().toString();
        try {
            return Integer.parseInt(name.substring(0, name.lastIndexOf('.')));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Expected numbered PGM file such as 1.pgm: " + image, error);
        }
    }

    /** Averages nine saved photo vectors to make one subject entry for this fold. */
    private static float[] averageOtherImages(List<float[]> vectors, int heldOut) {
        float[] average = new float[vectors.get(0).length];
        for (int image = 0; image < vectors.size(); image++) {
            if (image == heldOut) {
                continue;
            }
            float[] vector = vectors.get(image);
            for (int value = 0; value < average.length; value++) {
                average[value] += vector[value];
            }
        }
        for (int value = 0; value < average.length; value++) {
            average[value] /= IMAGE_COUNT - 1;
        }
        return average;
    }

    /** Explains exactly what each calculated number measures. */
    private static String buildReport(double threshold, int searches, int correctTop1,
                                      int correctTop3, long searchNanos) {
        double top1Percent = 100.0 * correctTop1 / searches;
        double top3Percent = 100.0 * correctTop3 / searches;
        double averageMillis = searchNanos / 1_000_000.0 / searches;
        return String.join(System.lineSeparator(),
                "FaceTrace Step 6 - ORL leave-one-out benchmark",
                "Subjects: " + SUBJECT_COUNT,
                "Images per subject: " + IMAGE_COUNT,
                "Folds: " + IMAGE_COUNT + " (each round holds out one image per subject)",
                "Training images per subject per fold: " + (IMAGE_COUNT - 1),
                "Searches completed: " + searches,
                String.format(Locale.ROOT, "Similarity threshold: %.4f", threshold),
                String.format(Locale.ROOT, "Top-1 accuracy: %.2f%% (%d/%d)", top1Percent, correctTop1, searches),
                String.format(Locale.ROOT, "Top-3 accuracy: %.2f%% (%d/%d)", top3Percent, correctTop3, searches),
                String.format(Locale.ROOT, "Average search time: %.4f ms", averageMillis),
                "Timing includes probe LBP extraction and ranking; it excludes file loading and gallery preparation.",
                "Meaning: top-1 is correct when the right subject ranks first; top-3 is correct when it appears among the first three.",
                "A probe with no candidate above the threshold counts as incorrect.",
                "Weak points: this is a small, controlled grayscale dataset with only known subjects in the gallery.",
                "LBP is a simple texture method; pose, lighting, expression, and image alignment can change its scores.",
                "The result is a classroom benchmark, not proof of identity or performance on new people.");
    }

    /** Keeps each subject's label, loaded images, and precomputed enrollment vectors together. */
    private record Subject(String caseCode, List<BufferedImage> images, List<float[]> embeddings) { }
}
