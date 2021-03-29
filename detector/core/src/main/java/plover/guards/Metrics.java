package plover.guards;

public class Metrics {

    private int potentialMethodCall;

    private int potentialInstruction;

    public Metrics(int potentialMethodCall, int potentialInstruction) {
        this.potentialMethodCall = potentialMethodCall;
        this.potentialInstruction = potentialInstruction;
    }

    public int getPotentialMethodCall() {
        return potentialMethodCall;
    }

    public void setPotentialMethodCall(int potentialMethodCall) {
        this.potentialMethodCall = potentialMethodCall;
    }

    public int getPotentialInstruction() {
        return potentialInstruction;
    }

    public void setPotentialInstruction(int potentialInstruction) {
        this.potentialInstruction = potentialInstruction;
    }

    @Override
    public String toString() {
        return "Metrics{" +
                "potentialMethodCall=" + potentialMethodCall +
                ", potentialInstruction=" + potentialInstruction +
                '}';
    }
}
