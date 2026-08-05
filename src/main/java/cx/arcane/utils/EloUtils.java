package cx.arcane.utils;

public class EloUtils {
    private long winnerRating;
    private long loserRating;
    private MatchConclusionType result;
    private double kFactorDefault = 10.0;
    private long minimumRating = 100;

    private long updatedWinnerRating;
    private long updatedLoserRating;

    public EloUtils() {
    }

    public EloUtils winnerRating(long rating) {
        this.winnerRating = rating;
        return this;
    }

    public EloUtils loserRating(long rating) {
        this.loserRating = rating;
        return this;
    }

    public EloUtils result(MatchConclusionType result) {
        this.result = result;
        return this;
    }

    public EloUtils kFactor(double k) {
        this.kFactorDefault = k;
        return this;
    }

    public EloUtils minimumRating(int min) {
        this.minimumRating = min;
        return this;
    }

    public EloUtils calculate() {
        double scoreWinner, scoreLoser;

        switch (result) {
            case WIN -> {
                scoreWinner = 1.0;
                scoreLoser = 0.0;
            }
            case DRAW -> {
                scoreWinner = 0.5;
                scoreLoser = 0.5;
            }
            default -> throw new IllegalArgumentException("Invalid Match Result");
        }

        double expectedWinner = expectedScore(winnerRating, loserRating);
        double expectedLoser = expectedScore(loserRating, winnerRating);

        updatedWinnerRating = newRating(winnerRating, scoreWinner, expectedWinner, kFactorDefault);
        updatedLoserRating = newRating(loserRating, scoreLoser, expectedLoser, kFactorDefault);

        // Clamp loser rating to minimum
        if (updatedLoserRating < minimumRating) {
            updatedLoserRating = minimumRating;
        }

        return this;
    }

    public long winnerResult() {
        return updatedWinnerRating;
    }

    public long loserResult() {
        return updatedLoserRating;
    }

    private double expectedScore(long ratingA, long ratingB) {
        return 1.0 / (1 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    private long newRating(long oldRating, double score, double expected, double k) {
        return (long) Math.round(oldRating + k * (score - expected));
    }

    public enum MatchConclusionType {
        WIN,   // winner defeats loser
        DRAW   // draw between both players
    }
}
