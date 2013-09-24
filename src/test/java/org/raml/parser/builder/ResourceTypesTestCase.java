package org.raml.parser.builder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;
import org.raml.model.ActionType;
import org.raml.model.Raml;
import org.raml.model.Resource;

public class ResourceTypesTestCase extends AbstractBuilderTestCase
{

    private static Raml raml;

    @BeforeClass
    public static void init()
    {
        raml = parseRaml("org/raml/types/resource-types.yaml");
    }

    @Test
    public void simple()
    {

        Resource simpleResource = raml.getResources().get("/simpleResource");
        assertThat(simpleResource.getActions().size(), is(1));
        assertThat(simpleResource.getAction(ActionType.GET).getSummary(), is("some summary"));
    }

    @Test
    public void optional()
    {
        Resource optionalResource = raml.getResources().get("/optionalResource");
        assertThat(optionalResource.getActions().size(), is(2));
        assertThat(optionalResource.getAction(ActionType.PUT).getSummary(), is("resource put summary"));
        assertThat(optionalResource.getAction(ActionType.PUT).getBody().size(), is(2));
        assertThat(optionalResource.getAction(ActionType.PUT).getBody().containsKey("application/json"), is(true));
        assertThat(optionalResource.getAction(ActionType.PUT).getBody().containsKey("text/xml"), is(true));
        assertThat(optionalResource.getAction(ActionType.POST).getSummary(), is("post summary"));
        assertThat(optionalResource.getAction(ActionType.POST).getBody().size(), is(1));
    }

    @Test
    public void parameters()
    {
        Resource paramsResource = raml.getResources().get("/paramsResource");
        assertThat(paramsResource.getActions().size(), is(1));
        assertThat(paramsResource.getAction(ActionType.PATCH).getSummary(), is("homemade summary"));

        Resource paramsResource2 = raml.getResources().get("/paramsResource2");
        assertThat(paramsResource2.getActions().size(), is(1));
        assertThat(paramsResource2.getAction(ActionType.DELETE).getSummary(), is("fine summary"));
    }

    @Test
    public void inheritance()
    {
        Resource inheritanceResource = raml.getResources().get("/inheritanceResource");
        assertThat(inheritanceResource.getActions().size(), is(2));
        assertThat(inheritanceResource.getAction(ActionType.GET).getSummary(), is("some summary"));
        assertThat(inheritanceResource.getAction(ActionType.POST).getBody().size(), is(1));
        assertThat(inheritanceResource.getAction(ActionType.POST).getBody().containsKey("text/yaml"), is(true));
    }

}