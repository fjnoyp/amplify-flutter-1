import 'package:flutter/foundation.dart';
import 'package:collection/collection.dart';
import 'package:flutter_modelschema/src/ModelSchema/types.dart';

import 'Post.dart';

/*
type Blog @model {
  id: ID!
  name: String!
  posts: [Post] @connection(keyName: "byBlog", fields: ["id"])
}
enum Status {

}
 */

@immutable
class Blog extends Model {
  static const classType = BlogType();

  final String id;
  final String name;
  final List<Post> posts;

  @override
  String getId() {
    return id;
  }

  const Blog._internal({@required this.id, @required this.name, this.posts});

  factory Blog({String id, @required String name, List<Post> posts}) {
    return Blog._internal(
        id: id == null ? UUID.getUUID() : id,
        name: name,
        posts: posts != null ? List.unmodifiable(posts) : posts);
  }

  bool equals(Object other) {
    return this == other;
  }

  @override
  bool operator ==(Object other) {
    if (identical(other, this)) return true;
    return other is Blog &&
        id == other.id &&
        name == other.name &&
        DeepCollectionEquality().equals(posts, other.posts);
  }

  @override
  int get hashCode => toString().hashCode;

  @override
  String toString() {
    var buffer = new StringBuffer();

    buffer.write("Blog {");
    buffer.write("id=" + id + ", ");
    buffer.write("name=" + name);
    buffer.write("}");

    return buffer.toString();
  }

  Blog copyWith(
      {@required String id, @required String name, List<Post> posts}) {
    return Blog(
        id: id ?? this.id, name: name ?? this.name, posts: posts ?? this.posts);
  }

  Blog.fromJson(Map<String, dynamic> json)
      : id = json['id'],
        name = json['name'],
        tags = json['tags'] is List
            ? (json['tags'] as List)
            : null
  posts = json['posts'] is List
  ? (json['posts'] as List)
      .map((e) => Post.fromJson(e as Map<String, dynamic>))
      .toList()
      : null;

  Map<String, dynamic> toJson() =>
      {'id': id, 'name': name, 'posts': posts.map((post) => post.toJson())};

}

class BlogType extends ModelType<Blog> {
  const BlogType();

  @override
  Blog createInstance() {
    // TODO: determine contents based on Datastore implemenation
    return Blog(); // FIXME
  }
}

extension BlogSchema on Blog {
  static final QueryField id = QueryField(fieldName: "id");
  static final QueryField name = QueryField(fieldName: "name");
  static final QueryField posts = QueryField(
      fieldName: "posts",
      fieldType: ModelFieldType(ModelFieldTypeEnum.model, ofModelName: "post"));

  static var schema =
  Model.defineSchema(define: (ModelSchemaDefinition modelSchemaDefinition) {
    modelSchemaDefinition.name = "Blog";
    modelSchemaDefinition.pluralName = "Blogs";

    modelSchemaDefinition.addField(ModelFieldDefinition.id());

    modelSchemaDefinition.addField(ModelFieldDefinition.field(
        key: BlogSchema.name,
        isRequired: true,
        ofType: ModelFieldType(ModelFieldTypeEnum.string)));

    modelSchemaDefinition.addField(ModelFieldDefinition.hasMany(
      key: BlogSchema.posts,
      isRequired: false,
      ofModelName: Post.classType.getTypeName(),// Post.type
      associatedKey: PostSchema.blog,
    ));
  });
}