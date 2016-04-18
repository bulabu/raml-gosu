package goson.typeloader.raml;

import gw.lang.reflect.IType;

public interface IRamlType extends IType {

  Object getDefaultValue(String method, String name);

  String getSchemaContent();
}
