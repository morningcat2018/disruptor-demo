package morning.cat.handle;

import com.lmax.disruptor.EventHandler;
import morning.cat.TaskEvent;

/**
 * 通过Disruptor传递数据时，对象的寿命可能超过预期。为避免这种情况发生，可能需要在处理事件后清除事件。
 * 如果您有一个事件处理程序清除同一个处理程序中的值就足够了。
 * 如果您有一系列事件处理程序，那么您可能需要在链的末尾放置一个特定的处理程序来处理清除对象。
 * <p>
 * 从环形缓冲区清除对象
 */
public class CleanTaskEventHandle implements EventHandler<TaskEvent> {
    @Override
    public void onEvent(TaskEvent event, long sequence, boolean endOfBatch) throws Exception {
        // Failing to call clear here will result in the
        // object associated with the event to live until
        // it is overwritten once the ring buffer has wrapped
        // around to the beginning.
        event.clear();
    }
}
