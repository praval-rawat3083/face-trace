package facetrace.model;

/** Holds one synthetic demo record or other record fields read from the database. */
public final class CriminalRecord {
    private final long id;
    private final String caseCode;
    private final String fullName;
    private final int age;
    private final String offenceType;
    private final String photoPath;

    /** Makes a new record that has not yet received a database id. */
    public CriminalRecord(String caseCode, String fullName, int age, String offenceType, String photoPath) {
        this(0, caseCode, fullName, age, offenceType, photoPath);
    }

    /** Makes a record with all fields, including its database id. */
    public CriminalRecord(long id, String caseCode, String fullName, int age, String offenceType, String photoPath) {
        this.id = id;
        this.caseCode = caseCode;
        this.fullName = fullName;
        this.age = age;
        this.offenceType = offenceType;
        this.photoPath = photoPath;
    }

    /** Makes a copy with the database id returned after saving. */
    public CriminalRecord withId(long newId) {
        return new CriminalRecord(newId, caseCode, fullName, age, offenceType, photoPath);
    }

    /** Gives the database row id, or zero before the row is saved. */
    public long getId() { return id; }

    /** Gives the case code stored for this row. */
    public String getCaseCode() { return caseCode; }

    /** Gives the demo subject name stored for this row. */
    public String getFullName() { return fullName; }

    /** Gives the age stored for this row. */
    public int getAge() { return age; }

    /** Gives the demo offence label stored for this row. */
    public String getOffenceType() { return offenceType; }

    /** Gives the path string stored for the associated image. */
    public String getPhotoPath() { return photoPath; }
}
