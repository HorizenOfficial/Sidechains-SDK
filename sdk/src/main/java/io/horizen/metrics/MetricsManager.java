package io.horizen.metrics;

import io.horizen.block.SidechainBlockBase;
import io.horizen.block.SidechainBlockBase$;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sparkz.core.utils.NetworkTimeProvider;

import java.io.IOException;


public class MetricsManager {

    protected static final Logger logger = LogManager.getLogger();
    private static MetricsManager me;

    private NetworkTimeProvider timeProvider;

    private Counter blocksAppliedSuccesfully;
    private Counter blocksNotApplied;
    private Gauge blockApplyTime;
    private Gauge blockApplyTimeAbsolute;
    private Gauge mempoolSize;

    private Gauge lotteryTime;

    public static MetricsManager getInstance(){
        if (me == null){
            throw new RuntimeException("Metrics manager not initialized!");
        }
        return me;
    }

    public static void init(NetworkTimeProvider timeProvider) throws IOException {
        me = new MetricsManager(timeProvider);
    }

    private MetricsManager(NetworkTimeProvider timeProvider) throws IOException {
        logger.debug("Initializing metrics engine");
        this.timeProvider = timeProvider;

        //JvmMetrics.builder().register(); // initialize the out-of-the-box JVM metrics

        blockApplyTime  = Gauge.builder()
                .name("block_applyTime")
                .help("Time to apply blocks (milliseconds)")
                .register();
        blockApplyTimeAbsolute = Gauge.builder()
                .name("block_applyTime_absolute")
                .help("Delta between block timestamp and timestamp when block has been applied succesfully (milliseconds)")
                .register();
        blocksAppliedSuccesfully = Counter.builder()
                .name("block_applied_ok")
                .help("Number of blocks applied succesfully")
                .register();
        blocksNotApplied = Counter.builder()
                .name("block_applied_ko")
                .help("Number of blocks not applied")
                .register();
        mempoolSize = Gauge.builder()
                .name("mempool_size")
                .help("Mempool size (number of transactions)")
                .register();
        lotteryTime  = Gauge.builder()
                .name("lottery_time")
                .help("Time to execute lottery (milliseconds)")
                .register();
    }

    public void appliedBlockOk(long blockTimestamp, long time){
        blockApplyTime.set(time);
        blockApplyTimeAbsolute.set(timeProvider.time() - blockTimestamp);
        blocksAppliedSuccesfully.inc();
    }
    public void appliedBlockKo(){
        blocksNotApplied.inc();
    }
    public void mempoolSize(int size){
        mempoolSize.set(size);
    }

    public void lotteryDone(long duration){
        lotteryTime.set(duration);
    }




}
