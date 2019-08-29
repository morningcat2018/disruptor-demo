package morning.cat;

/**
 * 任务事件
 */
public class TaskEvent {

    private Task value;

    public void set(Task task) {
        this.value = task;
    }

    public void clear() {
        value = null;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
