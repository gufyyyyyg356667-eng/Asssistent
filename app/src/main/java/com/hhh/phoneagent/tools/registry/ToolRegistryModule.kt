package com.hhh.phoneagent.tools.registry

import com.hhh.phoneagent.domain.tool.AgentTool
import com.hhh.phoneagent.tools.screen.FindElementsTool
import com.hhh.phoneagent.tools.screen.ReadScreenTool
import com.hhh.phoneagent.tools.ui.ClickTool
import com.hhh.phoneagent.tools.ui.GlobalActionTool
import com.hhh.phoneagent.tools.ui.ScrollTool
import com.hhh.phoneagent.tools.ui.SwipeTool
import com.hhh.phoneagent.tools.ui.TapTool
import com.hhh.phoneagent.tools.ui.TypeTextTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Register every AgentTool here. The registry rejects duplicate names at startup. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolRegistryModule {

    @Binds @IntoSet abstract fun readScreen(tool: ReadScreenTool): AgentTool
    @Binds @IntoSet abstract fun findElements(tool: FindElementsTool): AgentTool
    @Binds @IntoSet abstract fun click(tool: ClickTool): AgentTool
    @Binds @IntoSet abstract fun typeText(tool: TypeTextTool): AgentTool
    @Binds @IntoSet abstract fun scroll(tool: ScrollTool): AgentTool
    @Binds @IntoSet abstract fun tap(tool: TapTool): AgentTool
    @Binds @IntoSet abstract fun swipe(tool: SwipeTool): AgentTool
    @Binds @IntoSet abstract fun globalAction(tool: GlobalActionTool): AgentTool
}
