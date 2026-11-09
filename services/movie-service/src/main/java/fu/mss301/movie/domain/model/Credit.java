package fu.mss301.movie.domain.model;

public record Credit(
        Integer id, Integer personId, CreditType creditType, String characterName, Integer displayOrder) {
    public Credit validated() {
        if (personId == null || personId <= 0)
            throw new IllegalArgumentException("Person ID must be positive");
        if (creditType == null)
            throw new IllegalArgumentException("Credit type is required");
        if (displayOrder == null || displayOrder < 1)
            throw new IllegalArgumentException("Display order must be >= 1");
        String character = characterName == null || characterName.isBlank() ? null : characterName.trim();
        if (character != null && character.length() > 255)
            throw new IllegalArgumentException("Character name must not exceed 255 characters");
        if (creditType == CreditType.DIRECTOR && character != null)
            throw new IllegalArgumentException("Director credit must not have a character name");
        return new Credit(id, personId, creditType, character, displayOrder);
    }
    public Key key() {
        return new Key(personId, creditType);
    }
    public record Key(Integer personId, CreditType type) {}
}
