package goson.typeloader.raml;

import gw.lang.reflect.ITypeLoader;

import java.util.Map;

public class RamlType extends RamlTypeBase implements IRamlType {

  public RamlType(String name, ITypeLoader typeloader, Object object, Map<String, String> typeDefs,
                            Map<String, Map<String, Object>> defaultValues, String schema) {
    super(name, typeloader, object, typeDefs, defaultValues,  schema);
  }

  @Override
  protected RamlTypeInfo initTypeInfo(Object object) {
    return new RamlTypeInfo(RamlType.this);
  }
}
