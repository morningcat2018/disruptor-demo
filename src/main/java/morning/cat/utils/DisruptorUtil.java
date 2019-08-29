package morning.cat.utils;

import com.lmax.disruptor.dsl.Disruptor;

/**
 * @describe: 类描述信息
 * @author: morningcat.zhang
 * @date: 2019/8/29 5:19 PM
 */
public class DisruptorUtil {

    public static boolean isStarted(Disruptor<?> disruptor) {
        if (disruptor == null) {
            return false;
        }
        //{
        // ringBuffer=RingBuffer{bufferSize=1024, sequencer=AbstractSequencer{waitStrategy=BlockingWaitStrategy{processorNotifyCondition=java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@436a4e4b}, cursor=-1, gatingSequences=[-1]}},
        // started=false,
        // executor=BasicExecutor{threads=}
        // }
        String v = disruptor.toString().substring(disruptor.toString().indexOf("started"));
        String started = v.substring(0, v.indexOf(","));
        return started.contains("true");


    }
}
