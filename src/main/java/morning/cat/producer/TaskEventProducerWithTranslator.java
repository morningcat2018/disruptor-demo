package morning.cat.producer;

import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import morning.cat.Task;
import morning.cat.TaskEvent;

/**
 * Publishing Using Translators (3.x版本之后的生产者)
 */
public class TaskEventProducerWithTranslator {

    private final RingBuffer<TaskEvent> ringBuffer;

    public TaskEventProducerWithTranslator(RingBuffer<TaskEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    private static final EventTranslatorOneArg<TaskEvent, Task> TRANSLATOR =
            new EventTranslatorOneArg<TaskEvent, Task>() {
                @Override
                public void translateTo(TaskEvent event, long sequence, Task bb) {
                    event.set(bb);
                }
            };

    public void onData(Task bb) {
        //ringBuffer.publishEvent(TRANSLATOR, bb);
        ringBuffer.publishEvent((event, sequence) -> event.set(bb));
    }
}
