package io.horizen.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Info;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sparkz.core.utils.TimeProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MetricsManager {

    protected static final Logger logger = LogManager.getLogger();

    private TimeProvider timeProvider;
    private static MetricsManager me;

    private Info nodeInfo;
    private Counter blocksAppliedSuccessfully;
    private Counter blocksNotApplied;
    private Gauge blockApplyTime;
    private Gauge blockApplyTimeAbsolute;
    private Gauge mempoolSize;
    private Gauge forgeBlockCount;
    private Gauge forgeLotteryTime;
    private Gauge forgeBlockCreationTime;

    private List<MetricsHelp> helps;

    public static MetricsManager getInstance(){
        if (me == null){
            throw new RuntimeException("Metrics manager not initialized!");
        }
        return me;
    }

    public static void init(TimeProvider timeProvider) throws IOException {
        if (me == null){
            me = new MetricsManager(timeProvider);
        }
    }

    private MetricsManager(TimeProvider timeProvider) throws IOException {
        logger.debug("Initializing metrics engine");

        this.timeProvider = timeProvider;

        //JvmMetrics.builder().register(); // initialize the out-of-the-box JVM metrics
        helps = new ArrayList<>();

        nodeInfo = Info.builder().name("node_info").labelNames("version", "sdkVersion", "architecture", "jdkVersion").register();
        helps.add(new MetricsHelp(nodeInfo.getPrometheusName(), "Node version"));

        blockApplyTime  =  Gauge.builder().name("block_apply_time").register();
        helps.add(new MetricsHelp(blockApplyTime.getPrometheusName(), "Time to apply block to node wallet and state (milliseconds)"));

        blockApplyTimeAbsolute =  Gauge.builder().name("block_apply_time_fromslotstart").register();
        helps.add(new MetricsHelp(blockApplyTimeAbsolute.getPrometheusName(), "Delta between timestamp when block has been applied successfully on this node and start timestamp of the slot it belongs to (milliseconds)"));

        blocksAppliedSuccessfully =   Counter.builder().name("block_applied_ok").register();
        helps.add(new MetricsHelp(blocksAppliedSuccessfully.getPrometheusName(),"Number of received blocks applied successfully (absolute value since start of the node)"));

        blocksNotApplied =  Counter.builder().name("block_applied_ko").register();
        helps.add(new MetricsHelp(blocksNotApplied.getPrometheusName(), "Number of received blocks not applied (absolute value since start of the node)"));

        mempoolSize =  Gauge.builder().name("mempool_size").register();
        helps.add(new MetricsHelp(mempoolSize.getPrometheusName(), "Mempool size (number of transactions in this node mempool)"));

        forgeBlockCount = Gauge.builder().name("forge_block_count").register();
        helps.add(new MetricsHelp(forgeBlockCount.getPrometheusName(), "Number of forged blocks by this node (absolute value since start of the node)"));

        forgeLotteryTime = Gauge.builder().name("forge_lottery_time").register();
        helps.add(new MetricsHelp(forgeLotteryTime.getPrometheusName(), "Time to execute the lottery (milliseconds)"));

        forgeBlockCreationTime = Gauge.builder().name("forge_blockcreation_time").register();
        helps.add(new MetricsHelp(forgeBlockCreationTime.getPrometheusName(),  "Time to create a new forged block (calculated from the start timestamp of the slot it belongs to) (milliseconds)"));
    }

    public long currentMillis(){
        return timeProvider.time();
    }

    public List<MetricsHelp> getHelp(){
        return helps;
    }

    public void appliedBlockOk(long millis, long millisFromBlockStamp){
        blockApplyTime.set(millis);
        blockApplyTimeAbsolute.set(millisFromBlockStamp);
        blocksAppliedSuccessfully.inc();
    }

    public void setVersion(String version){ nodeInfo.setLabelValues(version.split("/"));}
    public void forgedBlock(long millis){
        forgeBlockCount.inc();
        forgeBlockCreationTime.set(millis);
    }
    public void appliedBlockKo(){
        blocksNotApplied.inc();
    }
    public void mempoolSize(int size){
        mempoolSize.set(size);
    }
    public void lotteryDone(long millis){
        forgeLotteryTime.set(millis);
    }


}
