package skaro.pokeapi.trainer.domain;

public final class StorageLimits {
    private final int partyMax;
    private final int boxMax;

    public StorageLimits(int partyMax, int boxMax) {
        if (partyMax < 1) throw new IllegalArgumentException("partyMax must be >= 1");
        if (boxMax < 1) throw new IllegalArgumentException("boxMax must be >= 1");
        this.partyMax = partyMax;
        this.boxMax = boxMax;
    }

    public int getPartyMax() { return partyMax; }
    public int getBoxMax() { return boxMax; }
}
