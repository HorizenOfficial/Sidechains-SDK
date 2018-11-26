import scorex.core.utils.ScorexEncoder;

public interface BoxUnlocker<P extends Proposition> extends scorex.core.transaction.box.BoxUnlocker
{
    // TO Do: replace with Score ADKey
    @Override
    Object closedBoxId();

    @Override
    Proof<P> boxKey();

    @Override
    String toString();

    // To Do: check this
    @Override
    ScorexEncoder encoder();
}
