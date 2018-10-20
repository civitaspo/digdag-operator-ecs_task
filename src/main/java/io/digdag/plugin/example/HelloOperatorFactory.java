package io.digdag.plugin.example;

import io.digdag.client.config.Config;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorContext;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskResult;
import io.digdag.spi.TemplateEngine;
import io.digdag.util.BaseOperator;

public class HelloOperatorFactory implements OperatorFactory {
    @SuppressWarnings("unused")
    private final TemplateEngine templateEngine;

    public HelloOperatorFactory(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String getType() {
        return "hello";
    }

    @Override
    public Operator newOperator(OperatorContext context) {
        return new HelloOperator(context);
    }

    private class HelloOperator extends BaseOperator {

        HelloOperator(OperatorContext context) {
            super(context);
        }

        @Override
        public TaskResult runTask() {
            //Config params = request.getConfig();
            Config params = request.getConfig().mergeDefault(
                request.getConfig().getNestedOrGetEmpty("hello"));

            String message = params.get("_command", String.class);
            message += params.get("message", String.class);

            System.out.println(message);

            return TaskResult.empty(request);
        }

    }
}
