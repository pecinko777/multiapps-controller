package com.sap.cloud.lm.sl.cf.core.dto.serialization.v3;

import java.util.Arrays;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(value = Parameterized.class)
public class DeployTargetDtoTest extends com.sap.cloud.lm.sl.cf.core.dto.serialization.v2.DeployTargetDtoTest {

    @Parameters
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
// @formatter:off
            {
                "platform-properties-v3.json", "platform-properties-v3.xml",
            },
// @formatter:on
        });
    }

    protected Class<? extends com.sap.cloud.lm.sl.cf.core.dto.serialization.DeployTargetDto> getTestedClass() {
        return DeployTargetDto.class;
    }

    public DeployTargetDtoTest(String json, String xml) throws Throwable {
        super(json, xml);
    }
}
