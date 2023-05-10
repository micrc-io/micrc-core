package io.micrc.core._camel.jit;

import org.kie.dmn.api.core.DMNResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * JITDMN服务实现
 *
 * @author hyosunghan
 * @date 2022/10/22 09:57
 * @since 0.0.1
 */
@Service
public class JITDMNService {

    public JITDMNResult evaluateModel(String modelXML, Map<String, Object> context) {
        DMNEvaluator dmnEvaluator = DMNEvaluator.fromXML(modelXML);
        DMNResult dmnResult = dmnEvaluator.evaluate(context);
        return new JITDMNResult(dmnEvaluator.getNamespace(), dmnEvaluator.getName(), dmnResult);
    }
}
