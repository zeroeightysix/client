package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.mixin.extension.mapBossInfos
import me.zeroeightsix.kami.mixin.extension.render
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.graphics.GlStateUtils
import net.minecraft.client.gui.BossInfoClient
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.RenderGameOverlayEvent
import org.lwjgl.opengl.GL11.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Module.Info(
        name = "BossStack",
        description = "Modify the boss health GUI to take up less space",
        category = Module.Category.RENDER
)
object BossStack : Module() {
    private val mode = register(Settings.e<BossStackMode>("Mode", BossStackMode.STACK))
    private val scale = register(Settings.floatBuilder("Scale").withValue(1.0f).withRange(0.1f, 5.0f))

    private enum class BossStackMode {
        REMOVE, MINIMIZE, STACK
    }

    private val texture = ResourceLocation("textures/gui/bars.png")
    private val bossInfoMap = LinkedHashMap<BossInfoClient, Int>()
    private val timer = TimerUtils.TickTimer()

    init {
        listener<RenderGameOverlayEvent.Pre> {
            if (it.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH) return@listener
            if (timer.tick(73L)) updateBossInfoMap()
            it.isCanceled = true
            drawHealthBar()
        }
    }

    private fun updateBossInfoMap() {
        bossInfoMap.clear()
        val bossInfoList = mc.ingameGUI.bossOverlay.mapBossInfos?.values ?: return

        when (mode.value!!) {
            BossStackMode.REMOVE -> {
            }
            BossStackMode.MINIMIZE -> {
                val closest = getMatchBoss(bossInfoList)?: return
                bossInfoMap[closest] = -1
            }
            BossStackMode.STACK -> {
                val cacheMap = HashMap<String, ArrayList<BossInfoClient>>()
                for (bossInfo in bossInfoList) {
                    val list = cacheMap.getOrPut(bossInfo.name.formattedText) { ArrayList() }
                    list.add(bossInfo)
                }
                for ((name, list) in cacheMap) {
                    val closest = getMatchBoss(list, name)?: continue
                    bossInfoMap[closest] = list.size
                }
            }
        }
    }

    private fun getMatchBoss(list: Collection<BossInfoClient>, name: String? = null): BossInfoClient? {
        val closestBossHealth = getClosestBoss(name)?.let {
            it.health / it.maxHealth * 100.0f
        } ?: return null

        return list.minBy {
            abs(it.percent - closestBossHealth)
        }
    }

    private fun getClosestBoss(name: String?) =
            mc.world?.loadedEntityList?.let {
                var closest = Float.MAX_VALUE
                var closestBoss: EntityLivingBase? = null
                for (entity in it) {
                    if (entity !is EntityLivingBase) continue
                    if (entity.isNonBoss) continue
                    if (name != null && entity.displayName.formattedText != name) continue
                    val dist = entity.getDistance(mc.player)
                    if (dist >= closest) continue
                    closest = dist
                    closestBoss = entity
                }
                closestBoss
            }

    private fun drawHealthBar() {
        mc.profiler.startSection("bossHealth")
        val width = ScaledResolution(mc).scaledWidth
        var posY = 12
        GlStateUtils.blend(true)
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        if (bossInfoMap.isNotEmpty()) for ((bossInfo, count) in bossInfoMap) {
            val posX = (width / scale.value / 2.0f - 91.0f).roundToInt()
            val text = bossInfo.name.formattedText + if (count != -1) " x$count" else ""
            val textPosX = width / scale.value / 2.0f - mc.fontRenderer.getStringWidth(text) / 2.0f
            val textPosY = posY - 9.0f

            glScalef(scale.value, scale.value, 1.0f)
            mc.textureManager.bindTexture(texture)
            mc.ingameGUI.bossOverlay.render(posX, posY, bossInfo)
            mc.fontRenderer.drawStringWithShadow(text, textPosX, textPosY, 0xffffff)
            glScalef(1.0f / scale.value, 1.0f / scale.value, 1.0f)

            posY += 10 + mc.fontRenderer.FONT_HEIGHT
        }
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        mc.profiler.endSection()
    }
}