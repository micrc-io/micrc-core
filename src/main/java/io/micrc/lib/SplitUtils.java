package io.micrc.lib;

import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionAdapter;

import java.util.List;

/**
 * 对象数组Split拆分器
 *
 * @author tengwang
 * @date 2022/11/9 15:51
 * @since 0.0.1
 */
public class SplitUtils extends ExpressionAdapter {

    @Override
    public Object evaluate(Exchange exchange) {
        @SuppressWarnings("unchecked")
        List<Object> objects = (List<Object>) exchange.getIn().getBody();
        if (null != objects) {
            return objects.iterator();
        } else {
            return null;
        }
    }
}
