package com.hhh.phoneagent.tools.registry

import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.domain.tool.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AgentTool>,
) : ToolRegistry {

    private val byName: Map<String, AgentTool>
    private val ordered: List<AgentTool>

    init {
        val map = tools.associateBy { it.name }
        require(map.size == tools.size) { "Duplicate AgentTool names are not allowed" }
        byName = map
        ordered = tools.sortedBy { it.name }
    }

    override fun find(name: String): AgentTool? = byName[name]

    override fun all(): List<AgentTool> = ordered

    override fun schemas(): List<ToolSchema> = ordered.map {
        ToolSchema(name = it.name, description = it.description, parameters = it.parameters)
    }
}
