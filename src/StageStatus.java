public enum StageStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    SKIPPED("Skipped");

    private final String label;

    StageStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
