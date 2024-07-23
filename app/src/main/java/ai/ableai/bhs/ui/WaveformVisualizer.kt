package ai.ableai.bhs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize

@Composable
fun WaveformVisualizer(
    modifier: Modifier,
    amplitudes: List<Float>,
    backgroundColor: Color = Color.Gray,
    waveformColor: Color = Color.LightGray,
    width: Float = 6f,
    distance: Float = 5f //파형간의 간격
) {
    var size by remember { mutableStateOf(Size.Zero) }
    Box(
        modifier = modifier
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                size =
                    coordinates.size.toSize() // coordinates.parentLayoutCoordinates?.size?.toSize()?: Size.Zero //
            }) {
            //drawRect(color = backgroundColor)

            val screenWidth = size.width
            val screenHeight = size.height
            val maxAmplitude = 1f //14000f
            // 스케일링 계수 계산
            val scalingFactor = 900f //screenHeight / maxAmplitude

            //val amps = amplitudes.takeLast((screenWidth / (width + distance)).toInt()).reversed()
            val amps = amplitudes
                .takeLast((screenWidth / (width + distance)).toInt())
                .map { it * scalingFactor } // 진폭 값 스케일링
                .reversed()

            amps.forEachIndexed { index, amp ->
                val left = screenWidth - index * (width + distance)
                val top = screenHeight / 2 - (amp + 20) / 2
                val right = left + width
                val bottom = top + amp + 20

                //Log.d("amp", "$amp $top $bottom")
                drawRoundRect(
                    color = waveformColor,
                    topLeft = Offset(left, top),
                    size = Size(width, bottom - top)
                )
            }
        }
    }
}
