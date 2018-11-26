import scala.collection.Seq;
import scorex.core.block.Block;
import scorex.core.transaction.state.Removal;


// TO DO: provide access to HistoryReader
public interface SidechainState {
    public boolean validate(BoxTransaction tx); // or (tx, block)

    // We should take in consideration to add a method that scans received block and eventually return a list
    // of outdated boxes (not coins boxes because coins cannot be destroyed/made them unusable arbitrarily!) to be removed
    // also if not yet opened.
    // For example this list can contains Ballots that happened in a past epoch or some other box that cannot be used anymore

    public SidechainState applyChanges(/*changes*/); // return Try[...]

    public SidechainState rollbackTo(scorex.core.VersionTag version); // return Try[...]
}


// TO DO: provide abstract class with OutOfBox persistence.
abstract class PersistentSidechainState implements SidechainState {

}
