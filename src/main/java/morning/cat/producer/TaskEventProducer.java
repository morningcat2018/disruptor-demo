package morning.cat.producer;

import com.lmax.disruptor.RingBuffer;
import morning.cat.Task;
import morning.cat.TaskEvent;

/**
 * Publishing Using the Legacy API (老版生产者)
 */
public class TaskEventProducer {

    private final RingBuffer<TaskEvent> ringBuffer;

    public TaskEventProducer(RingBuffer<TaskEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }


    public void onData(Task bb) {
        long sequence = ringBuffer.next();  // Grab the next sequence
        try {
            TaskEvent event = ringBuffer.get(sequence); // Get the entry in the Disruptor
            // for the sequence
            event.set(bb);  // Fill with data
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
