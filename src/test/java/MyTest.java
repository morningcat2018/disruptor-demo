import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import org.junit.Test;

/**
 * @describe: 类描述信息
 * @author: morningcat.zhang
 * @date: 2019/8/30 10:42 AM
 */
public class MyTest {

    @Test
    public void test1() {

        EventProcessor eventProcessor;
        // 事件处理器需要是一个可运行的实现，它将轮询来自环缓冲区的事件。使用适当的等待策略。您不太可能需要自己实现这个接口。
        // 首先看一下使用 eventhandler 接口和预先提供的 batcheventprocessor 实例。


        BatchEventProcessor batchEventProcessor;
        // 用于处理使用来自环缓冲区的条目的批处理语义的便利类
        // 并将可用事件委托给事件处理程序。


        EventHandler eventHandler;
        // 当事件在环缓冲区中可用时，为处理事件而实现的回调接口。



    }
}
