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
