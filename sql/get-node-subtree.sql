with recursive
    property(property_id, property_name, node_id, value_type, idx, property_idx) as (select property.id          as property_id,
                                                                                            property_type.name   as property_name,
                                                                                            property.ast_node_id as node_id,
                                                                                            property_type.value_type as value_type,
                                                                                            property.idx as idx,
                                                                                            property.property_idx as property_idx
                                                                                     from ast_node_property as property
                                                                                              join ast_node_property_type as property_type
                                                                                                   on property.ast_node_property_type_id = property_type.id),
    node(node_id, node_type, property_id) as (select node.id                   as node_id,
                                                     node_type.name            as node_type,
                                                     node.ast_node_property_id as property_id
                                              from ast_node as node
                                                       join ast_node_type as node_type on node.ast_node_type_id = node_type.id),

    leaf_code(node_id, property_id, property_name, value_type, idx, code)
        as (select node.node_id as node_id, property.property_id as property_id, property_name, value_type, idx, value as code
            from property
                     join ast_node_property_token as token on property.property_id = token.id
                     join node using (node_id)
            union all
            select node.node_id as node_id, property.property_id as property_id, property_name, value_type, idx, value as code
            from property
                     join ast_node_property_boolean as token on property.property_id = token.id
                     join node using (node_id)
            union all
            select node.node_id as node_id, property.property_id as property_id, property_name, value_type, idx, value as code
            from property
                     join ast_node_property_string as token on property.property_id = token.id
                     join node using (node_id)
            order by property_id desc
    ),
    node_code(state, node_id, property_name, idx, property_type, property_id, depth, code) as (
        select 'in_node', node_id, null, null, null, null, 1, node_type
        from node where node_id = 1114
        union all
        select 'in_property', node_code.node_id, property.property_name, 0, property.value_type, property.property_id, node_code.depth + 1, property.property_name
        from property
        join node_code on (node_code.node_id = property.node_id and node_code.state = 'in_node')
        where property.idx = 0
        union all
        select 'in_node', node.node_id, null, null, null, null, node_code.depth + 1, node_type
        from ast_node_property_node
        join node_code on
            state = 'in_property' and
            node_code.property_type = 'node' and
            node_code.property_id = ast_node_property_node.id
        join node on (ast_node_property_node.value = node.node_id)
        union all
        select 'in_leaf', node_code.node_id, node_code.property_name, node_code.idx, node_code.property_type, node_code.property_id, node_code.depth, leaf_code.code
        from leaf_code
                 join node_code on
                    state = 'in_property' and
                    node_code.property_type <> 'node' and
                    node_code.property_id = leaf_code.property_id
        union all
        select 'in_property', node_code.node_id, property.property_name, property.idx, property.value_type, property.property_id, node_code.depth, null
        from property
        join node_code on
            node_code.node_id = property.node_id and
            node_code.property_name = property.property_name and
            node_code.idx + 1 = property.idx and
            node_code.state = 'in_property'
        order by node_code.node_id, node_code.property_name

    )
select * from node_code;