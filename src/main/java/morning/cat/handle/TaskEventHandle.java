package morning.cat.handle;


import com.lmax.disruptor.EventHandler;
import morning.cat.TaskEvent;

/**
 * 事件处理器（消费者）
 */
public class TaskEventHandle implements EventHandler<TaskEvent> {

    /**
     * 业务处理
     *
     * @param event
     * @param sequence
     * @param endOfBatch
     * @throws Exception
     */
    @Override
    public void onEvent(TaskEvent event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println("业务处理: " + event);
    }
}
