package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.NodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Start node - entry point of the workflow.
 * Initializes workflow variables from user inputs.
 *
 * <p>对齐 Dify 0.x Python 实现:system 变量与 user inputs 是两条独立通道。</p>
 * <ul>
 *   <li><b>System 变量通道</b>: 写死 4 个固定键 (query / files / user_id / conversation_id),
 *       从 inputs.get("sys.X") 显式读取,直接走 VariablePool.setSystem → systemVariables 桶。</li>
 *   <li><b>自定义变量通道</b>: 从 inputs.get(variable.variable()) 扁平名读取,
 *       走 context.setVariable → nodeOutputs 桶。</li>
 * </ul>
 * <p>不允许通用 sys.X 命名空间(只支持 4 个固定键),与 Python 端 SystemVariableKey 枚举对齐。</p>
 */
public class StartNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(StartNode.class);

    public StartNode(String id, DifyNodeData data) {
        super(id, "start", data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing start node: {}", id);

        Map<String, Object> inputs = context.getInputs();

        // 1. 自定义变量通道:扁平名读取,写到 nodeOutputs[id] 桶
        //    对应 Python 端 add_node_inputs_to_pool(node_id=root_node_id, inputs=new_inputs)
        List<DifyVariable> variables = data.variables();
        if (variables != null) {
            for (DifyVariable variable : variables) {
                Object value = (inputs == null) ? null : inputs.get(variable.variable());

                // Use default value if input not provided
                if (value == null && variable.defaultValue() != null) {
                    value = variable.defaultValue();
                }

                context.setVariable(id, variable.variable(), value);
                log.debug("Start node initialized variable: {} = {}", variable.variable(), value);
            }
        }

        // 2. System 变量通道:写死 4 个固定 sys 键,显式读取
        //    对应 Python 端 build_system_variables(query=..., files=..., ...)
        //    不支持任意 sys.X 命名空间 —— 与 Dify SystemVariableKey 枚举对齐
        if (inputs != null) {
            // 关键修复:走 VariablePool.setSystem(写 systemVariables 桶),不能走通用 setVariable。
            //   通用 setVariable 会写到 nodeOutputs["sys"],而 resolveVariables 的 SYSTEM 分支
            //   从 getSystem(field) 走 systemVariables 桶,两条路径永不相交。
            //   与 conversation 通道修复(DifyWorkflowExecutor.java:235 改 setConversationVariable)对称。
            writeSystemVariable(context, "files", inputs.get("sys.files"));
            writeSystemVariable(context, "query", inputs.get("sys.query"));
            writeSystemVariable(context, "user_id", inputs.get("sys.user_id"));
            writeSystemVariable(context, "conversation_id", inputs.get("sys.conversation_id"));
        }

        // Set a completion marker
        context.setVariable(id, "_completed", true);
    }

    /**
     * 将 4 个固定 sys 键之一写入 systemVariables 桶。
     * 仅在 value 非 null 时写入,避免覆盖已存在的同名变量。
     */
    private static void writeSystemVariable(NodeExecutionContext context, String name, Object value) {
        if (value == null) {
            return;
        }
        context.getVariablePool().setSystem(name, value);
    }
}
