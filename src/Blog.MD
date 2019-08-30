

@[TOC](Disruptor 框架的简单实践)

- https://github.com/LMAX-Exchange/disruptor/wiki/Introduction
- https://github.com/LMAX-Exchange/disruptor/wiki/Getting-Started
- http://lmax-exchange.github.io/disruptor/files/Disruptor-1.0.pdf

## 基础知识

理解Disruptor是什么的最好方法是将它与目前很好理解和非常相似的东西进行比较。
在Disruptor的情况下，这将是Java的`BlockingQueue`。与队列一样，Disruptor的目的是在同一进程内的线程之间移动数据（例如消息或事件）。
但是，Disruptor提供了一些将其与队列区分开来的关键功能。他们是：
- 具有消费者依赖关系图的消费者多播事件。
- 为事件预先分配内存。
- 可选择无锁。

#### 架构图

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190829201110284.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3UwMTM4Mzc4MjU=,size_16,color_FFFFFF,t_70)

#### 核心概念

1. Ring Buffer

`环形缓冲区`通常被认为是`Disruptor`的主要方面，但是从3.0开始，Ring Buffer仅负责存储和更新通过Disruptor的数据（事件）。对于一些高级用例，可以完全由用户替换。

2. Sequence

Disruptor使用`Sequences`作为识别特定组件所在`位置`的方法。每个消费者（`EventProcessor`）都像Disruptor本身一样维护一个Sequence。大多数并发代码依赖于这些`Sequence值`的移动，因此Sequence支持AtomicLong的许多当前功能。事实上，与 2.x 之间唯一真正的区别是序列包含额外的功能，以防止序列和其他值之间的错误共享。

3. Sequencer

Sequencer是`Disruptor`的真正核心。该接口的2个实现（单生成器，多生产者）实现了所有并发算法，用于在生产者和消费者之间快速、正确地传递数据。

4. Sequence Barrier

`Sequence Barrier`由`Sequencer`产生，并包含对序列发生器中主要发布的序列和任何依赖消费者的序列的引用。它包含确定是否有任何可供消费者处理的事件的逻辑。

5. Wait Strategy

`等待策略`确定消费者如何等待生产者将事件放入`Disruptor`。有关可选锁定的部分中提供了更多详细信息。

6. Event

从生产者传递给消费者的`数据单位`。事件没有特定的代码表示，因为它`完全由用户定义`。

7. EventProcessor

用于处理来自`Disruptor`的事件的`主事件循环`，并拥有消费者序列的所有权。有一个名为`BatchEventProcessor`的表示，它包含`事件循环`的有效实现，并将回调到使用的提供的`EventHandler`接口实现。

8. EventHandler

由用户实现并代表Disruptor的使用者的接口。

9. Producer

这是调用Disruptor以将事件排入队列的用户代码。这个概念在代码中也没有表示。


#### Multicast Events


这是`队列`和`Disruptor`之间最大的行为差异。当您有多个消费者在同一个Disruptor上收听时，`所有事件都会发布给所有消费者`，而不是一个事件只发送给单个消费者的队列。
Disruptor 的行为旨在用于需要`对同一数据进行独立多个并行操作`的情况。来自LMAX的规范示例是我们有三个操作，即`日志记录`（将输入数据写入持久性日志文件），`复制`（将输入数据发送到另一台机器以确保存在数据的远程副本）和`业务逻辑`（真正的处理工作）。
使用 WorkerPool 也可以执行 Executor 样式的事件处理，其中通过并行处理不同事件来找到比例。请注意，它是在现有的 Disruptor 类之上进行的，并且不会使用相同的第一类支持进行处理，因此它可能不是实现该特定目标的最有效方法。

查看`上述架构图`可以看到有3个事件处理程序监听（JournalConsumer，ReplicationConsumer和ApplicationConsumer）到 Disruptor，这些事件处理程序中的每一个都将接收 Disruptor 中可用的所有消息（`按相同的顺序`）。这允许每个消费者的工作`并行运行`。

#### Consumer Dependency Graph

为了支持并行处理行为的实际应用，有必要支持消费者之间的协调。返回参考上述示例，必须防止业务逻辑消费者在日记和复制消费者完成其任务之前取得进展。
我们称这个概念为 `gating`，或者更准确地说，这种行为的超集特征称为 `gating（门控）`。
门控发生在两个地方。首先，我们需要确保`生产者不会超过消费者`。这是通过调用`RingBuffer.addGatingConsumers()`将相关的使用者添加到Disruptor来处理的。
其次，先前提到的情况是通过从必须首先完成其处理的组件构造包含序列的SequenceBarrier来实现的。

参考`上述架构图`，有3个消费者正在收听来自`Ring Buffer`的事件。此示例中有一个`依赖关系图`。
ApplicationConsumer 依赖于 JournalConsumer 和 ReplicationConsumer 。这意味着 JournalConsumer 和 ReplicationConsumer 可以彼此并行自由运行。
从ApplicationConsumer 的 SequenceBarrier 到 JournalConsumer 和 ReplicationConsumer 的序列的连接可以看到依赖关系。
值得注意的是 Sequencer 与下游消费者之间的关系。它的一个作用是确保发布不包装 Ring Buffer。
要做到这一点，下游消费者中没有一个可能具有低于环形缓冲区序列的序列，而不是环形缓冲区的大小。
但是，使用依赖关系图可以进行有趣的优化。由于 ApplicationConsumers Sequence 保证小于或等于 JournalConsumer 和 ReplicationConsumer（这是该依赖关系所确保的），
因此 Sequencer 只需要查看 ApplicationConsumer 的 Sequence。在更一般的意义上，Sequencer 只需要知道作为依赖关系树中叶节点的使用者的序列。

#### Event Preallocation

Disruptor 的目标之一是`在低延迟环境中使用`。在低延迟系统中，必须减少或移除内存分配。在基于Java的系统中，目的是减少由于垃圾收集导致的数量停滞。
为了支持这一点，用户可以预先分配 Disruptor 中事件所需的存储空间。在构造期间，EventFactory 由用户提供，并将在 Disruptor 的 Ring Buffer 中为每个条目调用。
将新数据发布到 Disruptor 时，API将允许用户获取构造的对象，以便他们可以调用方法或更新该存储对象上的字段。Disruptor 保证这些操作只要正确实现就是并发安全的。

#### Optionally Lock-free

`低延迟期望`推动的另一个关键实现细节是`广泛使用无锁算法`来实现`Disruptor`。所有内存可见性和正确性保证都是使用`内存屏障`和/或`比较和交换（CAS）`操作实现的。
只有一个用例需要实际锁定 是在 `BlockingWaitStrategy` 中。这仅仅是为了使用条件，以便在等待新事件到达时停放消耗线程。许多低延迟系统将使用`忙等待`来避免使用条件可能引起的抖动，但是在系统繁忙等待操作的数量中可能导致性能显着下降，尤其是在CPU资源严重受限的情况下。例如。虚拟化环境中的Web服务器。

## 简单实践

1. 任务类

```java
package morning.cat;

import lombok.Data;
import lombok.ToString;

/**
 * 任务模型
 */
@Data
@ToString
public class Task {

    private Long taskId;

    private String taskName;

    private String taskContent;
}

```

2. 任务事件类

```java
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

```

3. 事件工厂类

```java
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
```

4. 消费者 EventHandler

```java
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

```

// 清理 handle
```java
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

```

5. 生产者 

```java
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
```

```java
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
```

```java
package morning.cat.utils;

import com.lmax.disruptor.dsl.Disruptor;

public class DisruptorUtil {

    public static boolean isStarted(Disruptor<?> disruptor) {
        if (disruptor == null) {
            return false;
        }
        String v = disruptor.toString().substring(disruptor.toString().indexOf("started"));
        String started = v.substring(0, v.indexOf(","));
        return started.contains("true");
    }
}

```

6. 主体类

```java
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

```

---

## 博客和文章

demo code

- [github morningcat2018](https://github.com/morningcat2018/disruptor-demo)

How the Disruptor works and how to use it

- [环形缓冲区有什么特别之处？](http://mechanitis.blogspot.com/2011/06/dissecting-disruptor-whats-so-special.html)
- [如何从环形缓冲区读取？](http://mechanitis.blogspot.com/2011/06/dissecting-disruptor-how-do-i-read-from.html)
- [写入环形缓冲区](http://mechanitis.blogspot.com/2011/07/dissecting-disruptor-writing-to-ring.html)
- [无锁发布](http://blog.codeaholics.org/2011/the-disruptor-lock-free-publishing/)
- [用于连接Disruptor的DSL](http://www.symphonious.net/2011/07/11/lmax-disruptor-high-performance-low-latency-and-simple-too/)
- [Disruptor向导现在是Disruptor的一部分](http://www.symphonious.net/2011/08/13/the-disruptor-wizard-is-dead-long-live-the-disruptor-wizard/)
- [Disruptor 2.0版](http://mechanitis.blogspot.com/2011/08/disruptor-20-all-change-please.html)
- [在没有争用的情况下在线程之间共享数据](http://www.oraclejavamagazine-digital.com/javamagazine/20120304/?pg=56&pm=1&u1=friend)

Why the Disruptor is so fast

- [Locks Are Bad](http://mechanitis.blogspot.com/2011/07/dissecting-disruptor-why-its-so-fast.html)
- [魔术缓存行填充](http://mechanitis.blogspot.com/2011/07/dissecting-disruptor-why-its-so-fast_22.html)
- [揭开记忆障碍的神秘面纱](http://mechanitis.blogspot.com/2011/08/dissecting-disruptor-why-its-so-fast.html)

