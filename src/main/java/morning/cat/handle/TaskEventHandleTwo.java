package morning.cat.handle;

import com.lmax.disruptor.EventHandler;
import morning.cat.TaskEvent;

/**
 * @describe: 类描述信息
 * @author: morningcat.zhang
 * @date: 2019/8/30 1:50 PM
 */
public class TaskEventHandleTwo implements EventHandler<TaskEvent> {

    @Override
    public void onEvent(TaskEvent event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println(event.getTaskId() + " Two -------------------->" + event);
    }


}
