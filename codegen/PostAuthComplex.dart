import 'package:flutter/foundation.dart';
import 'package:flutter_modelschema/src/ModelSchema/types.dart';

import 'Post.dart';

/*
type PostAuthComplex
  @model
  @auth(
    rules: [
      { allow: owner, ownerField: "owner", operations: [create, update, delete, read] },
    ])
{
  id: ID!
  title: String!
  owner: String
}
 */

@immutable
class PostAuthComplex extends Model {
  static const classType = PostType();

  final String id;
  final String title;
  final String owner;

  @override
  String getId() {
    return id;
  }

  const PostAuthComplex._internal(
      {@required this.id, @required this.title, @required this.owner});

  factory PostAuthComplex(
      {String id, @required String title, @required String owner}) {
    return PostAuthComplex._internal(
        id: id == null ? UUID.getUUID() : id, title: title, owner: owner);
  }

  bool equals(Object other) {
    return this == other;
  }

  @override
  bool operator ==(Object other) {
    if (identical(other, this)) return true;
    return other is PostAuthComplex &&
        id == other.id &&
        title == other.title &&
        owner == other.owner;
  }

  @override
  int get hashCode => toString().hashCode;

  @override
  String toString() {
    var buffer = new StringBuffer();

    buffer.write("PostAuthComplex {");
    buffer.write("id=" + id + ", ");
    buffer.write("title=" + title + ", ");
    buffer.write("owner=" + owner);
    buffer.write("}");

    return buffer.toString();
  }

  PostAuthComplex copyWith(
      {@required String id, @required String title, @required String owner}) {
    return PostAuthComplex(
        id: id ?? this.id,
        title: title ?? this.title,
        owner: owner ?? this.owner);
  }

  PostAuthComplex.fromJson(Map<String, dynamic> json)
      : id = json['id'],
        title = json['title'],
        owner = json['owner'];

  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'owner': owner};
}

extension PostAuthComplexSchema on PostAuthComplex {
  static final QueryField id = QueryField(fieldName: "id");
  static final QueryField title = QueryField(fieldName: "title");
  static final QueryField owner = QueryField(fieldName: "owner");

  static var schema =
      Model.defineSchema(define: (ModelSchemaDefinition modelSchemaDefinition) {
    modelSchemaDefinition.name = "PostAuthComplex";
    modelSchemaDefinition.pluralName = "PostAuthComplexes";

    modelSchemaDefinition.authRules = [
      AuthRule(
          allow: AuthStrategy.owner,
          ownerField: "owner",
          identityClaim: "cognito:username",
          operations: [
            ModelOperation.create,
            ModelOperation.update,
            ModelOperation.delete,
            ModelOperation.read
          ])
    ];

    modelSchemaDefinition.pluralName = "PostAuthComplexes";

    modelSchemaDefinition.addField(ModelFieldDefinition.id());

    modelSchemaDefinition.addField(ModelFieldDefinition.field(
        key: PostAuthComplexSchema.title,
        isRequired: true,
        ofType: ModelFieldType(ModelFieldTypeEnum.string)));

    modelSchemaDefinition.addField(ModelFieldDefinition.field(
        key: PostAuthComplexSchema.owner,
        isRequired: false,
        ofType: ModelFieldType(ModelFieldTypeEnum.string)));
  });
}
