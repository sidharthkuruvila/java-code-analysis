# Denormalised queries

## A property of a node with property type information

```sql
select property.id          as property_id,
       property_type.name   as property_name,
       property.ast_node_id as node_id,
       property_type
from ast_node_property as property
         join ast_node_property_type as property_type on property.ast_node_property_type_id = property_type.id )
```

# Examples

## Construct the qualified names for name nodes.

```sql
with recursive
    node_property(property_id, property_name, node_id) as
        (select property.id          as property_id,
                property_type.name   as property_name,
                property.ast_node_id as node_id
         from ast_node_property as property
                  join ast_node_property_type as property_type
                       on property.ast_node_property_type_id = property_type.id),
    node(node_id, node_type, property_id) as
        (select node.id                   as node_id,
                node_type.name            as node_type,
                node.ast_node_property_id as property_id
         from ast_node as node
                  join ast_node_type as node_type on node.ast_node_type_id = node_type.id),
    name_node(node_id, identifier, qualifier_id) as
        (select node.node_id            as node_id,
                identifier_string.value as identifier,
                qualifier_node.id       as qualifier_id
         from node
                  join node_property as identifier
                       on identifier.node_id = node.node_id and
                          identifier.property_name = 'identifier'
                  left join node_property as qualifier
                            on qualifier.node_id = node.node_id and
                               qualifier.property_name = 'qualifier'
                  join ast_node_property_string as identifier_string
                       on identifier_string.id = identifier.property_id
                  left join ast_node as qualifier_node
                            on qualifier_node.ast_node_property_id = qualifier.property_id),
    full_name(node_id, name) as
        (select node_id, identifier as name
         from name_node
         where qualifier_id is null
         union
         select name_node.node_id as node_id, (full_name.name || '.' || identifier)
         from name_node
                  join full_name on full_name.node_id = name_node.qualifier_id)
select *
from node
         join full_name using (node_id)
where node_type = 'Name' limit 10;
```

## Find the name nodes for annotation expressions

```sql
with name_node_string_value(node_id, value) as
         (select ast_node.id as node_id,
                 ast_node_property_string.value as value
from ast_node
    join ast_node_type
on (ast_node_type.id = ast_node.ast_node_type_id)
    join ast_node_property on ast_node.id = ast_node_property.ast_node_id
    join ast_node_property_type
    on ast_node_property_type.id = ast_node_property.ast_node_property_type_id
    join ast_node_property_string on ast_node_property.id = ast_node_property_string.id
where ast_node_type.name = 'Name')
    , annotation_node(node_id) as (
select ast_node.id as node_id
from ast_node
    join ast_node_type
on ast_node_type.id = ast_node.ast_node_type_id
where ast_node_type.name like '%AnnotationExpr%')
    , annotation_name_node(node_id
    , name_node_id) as (
select ast_node.id as node_id, child_node.id as name_node_id
from ast_node
    join ast_node_property
on ast_node.id = ast_node_property.ast_node_id
    join ast_node_property_type
    on ast_node_property.ast_node_property_type_id =
    ast_node_property_type.id
    join ast_node as child_node
    on child_node.ast_node_property_id = ast_node_property.id
    join ast_node_type as child_node_type
    on child_node.ast_node_type_id = child_node_type.id
where ast_node_property_type.name = 'name')
select *
from annotation_name_node
         join name_node_string_value on (name_node_string_value.node_id = annotation_name_node.name_node_id) limit 10;
```