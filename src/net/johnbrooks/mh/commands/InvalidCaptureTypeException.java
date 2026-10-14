package net.johnbrooks.mh.commands;

public class InvalidCaptureTypeException extends RuntimeException {

    private final String typeId;
    private final String availableTypes;

    public InvalidCaptureTypeException(String typeId, String availableTypes) {
        super("Unknown capture type: " + typeId);
        this.typeId = typeId;
        this.availableTypes = availableTypes;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getAvailableTypes() {
        return availableTypes;
    }
}
