package com.motionstudio.part1

data class NodePort(val id: String, val label: String)

data class GraphNode(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    val inputs: MutableList<NodePort> = mutableListOf(),
    val outputs: MutableList<NodePort> = mutableListOf(),
    var x: Float = 0f,
    var y: Float = 0f
)

data class GraphConnection(
    val fromNode: String,
    val fromPort: String,
    val toNode: String,
    val toPort: String
)

class NodeGraph {
    val nodes = mutableListOf<GraphNode>()
    val connections = mutableListOf<GraphConnection>()

    fun add(node: GraphNode) { nodes.add(node) }

    fun connect(c: GraphConnection) {
        require(nodes.any { it.id == c.fromNode })
        require(nodes.any { it.id == c.toNode })
        connections.add(c)
    }

    fun topologicalOrder(): List<GraphNode> {
        val incoming = nodes.associate { it.id to 0 }.toMutableMap()
        connections.forEach { incoming[it.toNode] = (incoming[it.toNode] ?: 0) + 1 }
        val queue = ArrayDeque(nodes.filter { incoming[it.id] == 0 })
        val out = mutableListOf<GraphNode>()

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            out += n
            connections.filter { it.fromNode == n.id }.forEach { c ->
                incoming[c.toNode] = incoming[c.toNode]!! - 1
                if (incoming[c.toNode] == 0) nodes.first { it.id == c.toNode }.let(queue::addLast)
            }
        }
        check(out.size == nodes.size) { "Node graph contains a cycle." }
        return out
    }
}
