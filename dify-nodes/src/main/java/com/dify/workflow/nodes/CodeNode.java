package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code node - executes code (JavaScript via Nashorn).
 *
 * <p>使用 JDK 8 内置 Nashorn 引擎（通过 javax.script SPI）。
 * 不直接引用 jdk.nashorn.api.scripting.*（该包在 Java 8 javac 中受 ct.sym 限制）。</p>
 */
public class CodeNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(CodeNode.class);

    public CodeNode(String id, DifyNodeData data) {
        super(id, NodeType.CODE, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing code node: {}", id);

        String language = data.language();
        if (language == null || language.trim().isEmpty()) {
            language = "javascript";
        }
        if (!"javascript".equalsIgnoreCase(language) && !"js".equalsIgnoreCase(language)) {
            throw new IllegalArgumentException("Code node only supports JavaScript, got: " + language);
        }

        String code = data.code();
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code node has no code: " + id);
        }

        // 关键修复:对齐 Dify 0.x 网页端 Code 节点默认 ES6 模板
        //   Dify UI 生成的 code 节点默认用 const/let 声明(ES6) 和 function main({X, Y}) 解构参数,
        //   但 Java 8 内置 Nashorn 1.8.x 是 ECMAScript 5.1,不支持这些 ES6 特性。
        //   eval 前做最小源转换:
        //     1. const/let → var (避免 Expected operand but found const 错误)
        //     2. for(let i = 0; ...;) → for(var i = 0; ...;) (let 在 for 头也要替换)
        //     3. function main({X, Y}) {body} → function main(__args__) {var X=__args__.X; var Y=__args__.Y; body}
        //        (解构参数转 ES5 等价形式)
        //   当前 testchatflow2.yml 中 5 个 code 节点:
        //     - 1780076867400 / 17802193695580: 各有 3 个 const + 1 个 for-let
        //     - 1027/1425/1522: 3 个 function main({X}) / ({X, Y}) 解构
        //   全部由这一段兜底。
        code = code
                .replaceAll("\\bconst\\s+", "var ")
                .replaceAll("\\bfor\\s*\\(\\s*let\\s+", "for(var ");
        code = transformDestructuringParams(code);

        // 读取输入变量
        List<com.dify.workflow.model.DifyVariable> inputVars = data.inputVariables();
        Map<String, Object> args = new HashMap<>();
        if (inputVars != null) {
            for (com.dify.workflow.model.DifyVariable v : inputVars) {
                Object value = context.getVariable(v.valueSelector().get(0), v.valueSelector().get(1));
                if (value != null) {
                    args.put(v.variable(), value);
                }
            }
        }

        // 执行 JavaScript
        // 关键:每次 doExecute 都新建 ScriptEngine 实例,避免全局 var/function 累积污染。
        //   之前在方法外持有 ScriptEngine 引用,loop/iteration body 内多次执行同一段 code,
        //   Nashorn 抛 TypeError: variable already defined,被 catch 静默吞掉,
        //   resultMap 退化为空 Map,fallback 把 result(=空 ScriptObjectMirror)赋给所有 output key,
        //   arrayLikeMapToList 把空 Map 转成空 List,下游节点拿到 a=[]/b=[]/index=[]。
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        if (engine == null) {
            engine = manager.getEngineByName("js");
        }
        if (engine == null) {
            throw new IllegalStateException("Nashorn JavaScript engine not available");
        }

        // 关键修复:把 args (Java Map) 转成 JS 对象再 invokeFunction。
        //   Nashorn 对 Java Map 只支持方括号索引 (arg1['a']),不支持点访问 (arg1.a),
        //   因为 Map 没有 getA() / getArg1() 之类的 bean 方法。如果直接把
        //   java.util.HashMap 作为 args 传给 main(args),用户写 `args.arg1` 会拿到
        //   undefined,JS 链路传染到下游节点产生 `[]+[]=0.0` 的伪通过。
        //   FastJSON2 toJSONString 把 Map/List 转 JSON,eval("(" + json + ")") 解析
        //   为标准 JS 对象,点和方括号双访问都支持,function main({arg1}) 解构也兼容。
        String jsonArgs = com.alibaba.fastjson2.JSON.toJSONString(args);
        Object jsArgs = engine.eval("(" + jsonArgs + ")");


        // 兼容 3 种用户 JS 写法:
        //   (a) `function main(args) { return X; }` — Invocable.invokeFunction("main", args)
        //   (b) `function main() { return X; } main();` — eval 自身返回 X
        //   (c) `var x = ...; x` — eval 自身返回 x
        // 关键:必须用 Invocable.invokeFunction 才能把 args 传给 main。
        //   Invocable 调时,JS 数组会被 Nashorn 自动转"整数 key Map",
        //   我们用 arrayLikeMapToList 显式转回 List。
        Object result = null;
        try {
            engine.eval(code);
            if (engine instanceof javax.script.Invocable) {
                javax.script.Invocable inv = (javax.script.Invocable) engine;
                try {
                    result = inv.invokeFunction("main", jsArgs);
                } catch (Exception noMain) {
                    // 关键修复:捕获所有 Exception 而非仅 ScriptException。
                    //   如果 main 抛错(TypeError 等),invokeFunction 包成 ScriptException,
                    //   之前 catch 只接 ScriptException,Nashorn 偶尔抛 NoSuchMethodException
                    //   会绕过 catch 走 line 105-107 的 rethrow,workflow 整个失败。
                    log.warn("Code node {} invokeFunction failed: {} (will try eval fallback)",
                            id, noMain.getMessage());
                    result = null;
                }
            }
        } catch (Exception e) {
            // eval(code) 自身抛错(语法错误等),直接抛出,让 executor 标 ERROR
            throw e;
        }
        // 兜底 (b)/(c) 形式:用 eval(code) 的返回值作为 main 的返回值
        if (result == null) {
            try {
                // 关键:不要用同一 engine 再次 eval(同一实例内 var 重定义会抛错),
                // 改用一个新的 freshScriptEngine 来执行用户 code,避免污染当前 engine 状态。
                ScriptEngine freshEngine = manager.getEngineByName("nashorn");
                if (freshEngine == null) {
                    freshEngine = manager.getEngineByName("js");
                }
                if (freshEngine != null) {
                    result = freshEngine.eval(code);
                }
            } catch (Exception ignore) {
                log.warn("Code node {} eval fallback failed: {}", id, ignore.getMessage());
            }
        }

        log.debug("Code node {} raw result type: {}",
            id, result == null ? "null" : result.getClass().getName());

        // 决定要写哪些 key:
        //   1. 优先 yml 的 data.outputs() 字段(Map<String,OutputSchema> — yml 实际用法)
        //   2. fallback data.outputVariables()(List<DifyVariable> — 名字字段)
        //   3. 兜底:JS 返回值自身的 keys
        Set<String> outputKeys = new LinkedHashSet<>();
        Object outputsField = data.outputs();
        if (outputsField instanceof Map) {
            for (Object k : ((Map<?, ?>) outputsField).keySet()) {
                if (k != null) {
                    outputKeys.add(k.toString());
                }
            }
        }
        List<com.dify.workflow.model.DifyVariable> outputVars = data.outputVariables();
        if (outputVars != null) {
            for (com.dify.workflow.model.DifyVariable v : outputVars) {
                if (v.variable() != null) {
                    outputKeys.add(v.variable());
                }
            }
        }

        // Nashorn 反射转换 ScriptObjectMirror → Map(避免 ct.sym import 限制)
        // 进一步递归 unwrap,确保所有 ScriptObjectMirror 转成 Java List/Map(下游节点 instanceof List 才会通过)
        Object unwrapped = Java8Compat.unwrapNashorn(result);
        Map<String, Object> resultMap;
        if (unwrapped instanceof Map) {
            resultMap = (Map<String, Object>) unwrapped;
        } else {
            resultMap = Java8Compat.toMap(result);
        }
        if (resultMap == null) {
            resultMap = new HashMap<>();
        }
        if (outputKeys.isEmpty()) {
            for (Object k : resultMap.keySet()) {
                if (k != null) {
                    outputKeys.add(k.toString());
                }
            }
        }

        for (String key : outputKeys) {
            Object value = resultMap.get(key);
            if (value == null) {
                value = result; // 单返回值 fallback
            }
            // 关键:Nashorn eval 返回的 JS 数组被自动转成整数 key HashMap,
            // 这里识别并转成 List,让下游 instanceof List 检查通过
            value = Java8Compat.arrayLikeMapToList(value);
            // 关键修复:用当前 code 节点 id 写 context(之前用 valueSelector 写错了 nodeId)
            context.setVariable(id, key, value);
            log.debug("Code node {} output {} type={} value={}", id, key,
                value == null ? "null" : value.getClass().getName(), value);
        }

        log.debug("Code node {} executed successfully", id);
    }

    /**
     * 关键修复:把 ES6 解构参数 function main({X, Y}) {body} 改写为 ES5 等价形式
     *   function main(__args__) {var X=__args__.X; var Y=__args__.Y; body}
     * Nashorn 1.8 不支持参数解构,Dify UI 默认生成的 code 模板用解构会 syntax error。
     * 只处理 `function main({...})` 这一种签名(项目 yml 中所有解构都在 main 入口);
     * 不处理嵌套对象的解构(`{a: {b}}`)、默认值(`{a = 1}`)、rest 模式(`{...rest}`),
     * 对当前 yml 已足够。
     */
    private static String transformDestructuringParams(String code) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "function\\s+main\\s*\\(\\s*\\{([^)]+?)\\}\\s*\\)\\s*\\{");
        java.util.regex.Matcher m = p.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String params = m.group(1);
            String[] names = params.split(",");
            StringBuilder vars = new StringBuilder();
            for (String n : names) {
                String n2 = n.trim();
                if (!n2.isEmpty()) {
                    vars.append("var ").append(n2).append("=__args__.").append(n2).append(";");
                }
            }
            m.appendReplacement(sb, "function main(__args__){" + vars);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
