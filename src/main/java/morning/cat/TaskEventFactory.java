package morning.cat;

import com.lmax.disruptor.EventFactory;

/**
 * 任务事件工厂类
 */
public class TaskEventFactory implements EventFactory<TaskEvent> {
    @Override
    public TaskEvent newInstance() {
        return new TaskEvent();
    }
}
