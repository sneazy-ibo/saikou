package ani.saikou.settings

import ani.saikou.R
import ani.saikou.currContext
import java.io.Serializable

data class CurrentReaderSettings(
    var direction: Directions = Directions.TOP_TO_BOTTOM,
    var layout: Layouts = Layouts.CONTINUOUS,
    var dualPageMode: DualPageModes = DualPageModes.Automatic,
    var overScrollMode: Boolean = true,
    var trueColors: Boolean = false,
    var rotation: Boolean = true,
    var padding: Boolean = true,
    var hidePageNumbers: Boolean = false,
    var horizontalScrollBar: Boolean = true,
    var keepScreenOn: Boolean = false,
    var volumeButtons: Boolean = false,
    var wrapImages: Boolean = false,
    var longClickImage: Boolean = true,
    var cropBorders: Boolean = false,
    var cropBorderThreshold: Int = 10,
) : Serializable {

    enum class Directions(val string: String) {

        TOP_TO_BOTTOM(currContext()!!.getString(R.string.top_to_bottom)),
        RIGHT_TO_LEFT(currContext()!!.getString(R.string.right_to_left)),
        BOTTOM_TO_TOP(currContext()!!.getString(R.string.bottom_to_top)),
        LEFT_TO_RIGHT(currContext()!!.getString(R.string.left_to_right));

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    enum class Layouts(val string: String) {
        PAGED(currContext()!!.getString(R.string.paged)),
        CONTINUOUS_PAGED(currContext()!!.getString(R.string.continuous_paged)),
        CONTINUOUS(currContext()!!.getString(R.string.continuous));

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    enum class DualPageModes {
        No, Automatic, Force;

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    companion object {
        fun applyWebtoon(settings: CurrentReaderSettings) {
            settings.apply {
                layout = Layouts.CONTINUOUS
                direction = Directions.TOP_TO_BOTTOM
                dualPageMode = DualPageModes.No
                padding = false
            }
        }
    }
}

