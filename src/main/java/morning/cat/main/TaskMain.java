package morning.cat.main;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import morning.cat.Task;
import morning.cat.TaskEvent;
import morning.cat.TaskEventFactory;
import morning.cat.handle.CleanTaskEventHandle;
import morning.cat.handle.TaskEventHandle;
import morning.cat.producer.TaskEventProducer;
import morning.cat.producer.TaskEventProducerWithTranslator;
import morning.cat.utils.DisruptorUtil;


public class TaskMain {

    public static void main(String[] args) throws Exception {
        // The factory for the event
        TaskEventFactory factory = new TaskEventFactory();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;

        // Construct the Disruptor
        Disruptor<TaskEvent> disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);
        // The default wait strategy used by the Disruptor is the BlockingWaitStrategy.
        // 在内部，BlockingWaitStrategy使用典型的锁和条件变量来处理线程唤醒。
        // BlockingWaitStrategy是可用等待策略中最慢的，但在CPU使用率方面是最保守的，并且将在最广泛的部署选项中提供最一致的行为。但是，再次了解已部署的系统可以实现额外的性能。

        // Construct the Disruptor with a SingleProducerSequencer
        Disruptor<TaskEvent> disruptor2 = new Disruptor(
                factory, bufferSize, DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, new YieldingWaitStrategy());
        // SleepingWaitStrategy -> 与BlockingWaitStrategy一样，SleepWaitStrategy通过使用简单的忙等待循环尝试保守CPU使用，但在循环中间使用LockSupport.parkNanos（1）调用。在典型的Linux系统上，这将使线程暂停约60μs。然而，它具有以下好处：生产线程不需要采取任何其他增加适当计数器的动作，并且不需要发信号通知条件变量的成本。但是，在生产者和消费者线程之间移动事件的平均延迟会更高。它在不需要低延迟的情况下效果最佳，但是对生产线程的影响很小。常见用例是异步日志记录。
        // YieldingWaitStrategy -> YieldingWaitStrategy是可以在低延迟系统中使用的2种等待策略之一，其中可以选择以提高延迟为目标来刻录CPU周期。YieldingWaitStrategy将忙于等待序列增加到适当的值。在循环体内，将调用Thread.yield()，允许其他排队的线程运行。当需要非常高的性能并且事件处理程序线程的数量小于逻辑核心的总数时，这是推荐的等待策略，例如，你启用了超线程。
        // BusySpinWaitStrategy -> BusySpinWaitStrategy是性能最高的等待策略，但对部署环境施加了最高限制。仅当事件处理程序线程的数量小于框中的物理核心数时，才应使用此等待策略。例如。应禁用超线程。

        // Connect the handler
        disruptor.handleEventsWith(new TaskEventHandle())
                // .then(new CleanTaskEventHandle()) // 从环形缓冲区清除对象
        ;

        // Start the Disruptor, starts all threads running
        // disruptor.start();

        if (!DisruptorUtil.isStarted(disruptor)) {
            disruptor.start();
        }

        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<TaskEvent> ringBuffer = disruptor.getRingBuffer();

        // Producer
        TaskEventProducer producer = new TaskEventProducer(ringBuffer);
        TaskEventProducerWithTranslator translator = new TaskEventProducerWithTranslator(ringBuffer);

        for (long l = 0; true; l++) {
            Task task = new Task();
            task.setTaskId(System.currentTimeMillis());
            task.setTaskName(Thread.currentThread().getName() + task.getTaskId());
            task.setTaskContent("Hello World,this is disruptor");
            producer.onData(task);

            Thread.sleep(100);

            Task task2 = new Task();
            task2.setTaskId(System.currentTimeMillis());
            task2.setTaskName(Thread.currentThread().getName() + task.getTaskId());
            task2.setTaskContent("Hello World,this is task2");
            translator.onData(task2);
        }
    }
}
