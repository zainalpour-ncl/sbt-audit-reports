package com.ncl.audit.cli.model

import com.ncl.audit._
import zio.json._

object JsonCodecs {
  implicit val rpcMethodEncoder: JsonEncoder[RpcMethod] = DeriveJsonEncoder.gen[RpcMethod]
  implicit val methodCallEncoder: JsonEncoder[MethodCall] = DeriveJsonEncoder.gen[MethodCall]
  implicit val serviceCallEncoder: JsonEncoder[ServiceCall] = DeriveJsonEncoder.gen[ServiceCall]
  implicit val projectRefEncoder: JsonEncoder[ProjectRef] = DeriveJsonEncoder.gen[ProjectRef]
  implicit val projectDependencyEncoder: JsonEncoder[ProjectDependency] = DeriveJsonEncoder.gen[ProjectDependency]
  implicit val serviceEncoder: JsonEncoder[Service] = DeriveJsonEncoder.gen[Service]
  implicit val samlConfigurationEncoder: JsonEncoder[SamlConfiguration] = DeriveJsonEncoder.gen[SamlConfiguration]
  implicit val restEndpointEncoder: JsonEncoder[RestEndpoint] = DeriveJsonEncoder.gen[RestEndpoint]
  implicit val projectModelEncoder: JsonEncoder[ProjectModel] = DeriveJsonEncoder.gen[ProjectModel]
}

