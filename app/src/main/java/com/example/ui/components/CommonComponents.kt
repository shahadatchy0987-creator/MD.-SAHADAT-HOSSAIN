package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MonthlyChartPoint
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.Formatters
import com.example.model.AccountScope
import com.example.model.PaymentMethod
import com.example.model.TransactionRecord
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.ExpenseCoralBg
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenBg
import com.example.ui.theme.MonoAmountStyle
import com.example.ui.theme.TransferIndigo
import com.example.ui.theme.TransferIndigoBg

@Composable
fun KpiMetricCard(
  title: String,
  amount: Double,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier =
            Modifier.size(30.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(17.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = Formatters.formatTaka(amount),
        style = MonoAmountStyle.copy(fontSize = 17.sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun MonthlyIncomeExpenseChart(
  data: List<MonthlyChartPoint>,
  lang: AppLang,
  modifier: Modifier = Modifier,
) {
  var selectedIndex by remember(data) { mutableIntStateOf((data.size - 1).coerceAtLeast(0)) }
  val maxVal = (data.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0.0).coerceAtLeast(1.0)

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = AppStrings.monthlyChartTitle(lang),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          LegendDot(color = IncomeGreen, label = AppStrings.income(lang))
          LegendDot(color = ExpenseCoral, label = AppStrings.expense(lang))
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (data.all { it.income == 0.0 && it.expense == 0.0 }) {
        Box(
          modifier = Modifier.fillMaxWidth().height(120.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = AppStrings.tr(lang, "Add income or expense entries to view monthly chart", "মাসিক চার্ট দেখতে আয় বা ব্যয় যোগ করুন"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        Canvas(
          modifier =
            Modifier.fillMaxWidth()
              .height(145.dp)
              .padding(horizontal = 4.dp)
        ) {
          val groupCount = data.size.coerceAtLeast(1)
          val groupWidth = size.width / groupCount
          val barWidth = (groupWidth * 0.28f).coerceAtMost(26f)
          val chartBottom = size.height - 8f
          val chartHeight = chartBottom - 8f

          // Subtle horizontal guide lines
          listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
            val yPos = chartBottom - chartHeight * ratio
            drawLine(
              color = gridColor,
              start = Offset(0f, yPos),
              end = Offset(size.width, yPos),
              strokeWidth = 1.5f,
            )
          }

          data.forEachIndexed { idx, point ->
            val centerX = idx * groupWidth + groupWidth / 2f
            val incH = ((point.income / maxVal).toFloat() * chartHeight).coerceAtLeast(if (point.income > 0) 6f else 0f)
            val expH = ((point.expense / maxVal).toFloat() * chartHeight).coerceAtLeast(if (point.expense > 0) 6f else 0f)

            drawRoundRect(
              color = IncomeGreen,
              topLeft = Offset(centerX - barWidth - 3f, chartBottom - incH),
              size = Size(barWidth, incH),
              cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
              color = ExpenseCoral,
              topLeft = Offset(centerX + 3f, chartBottom - expH),
              size = Size(barWidth, expH),
              cornerRadius = CornerRadius(6f, 6f),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          data.forEachIndexed { idx, point ->
            val isSelected = idx == selectedIndex
            Surface(
              shape = RoundedCornerShape(8.dp),
              color =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
              modifier =
                Modifier.clickable { selectedIndex = idx }.padding(horizontal = 2.dp),
            ) {
              Text(
                text = point.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

        val activePoint = data.getOrNull(selectedIndex)
        if (activePoint != null) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = activePoint.monthKey,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "+${Formatters.formatTaka(activePoint.income)}",
                style = MonoAmountStyle.copy(fontSize = 12.sp),
                color = IncomeGreen,
              )
              Text(
                text = "-${Formatters.formatTaka(activePoint.expense)}",
                style = MonoAmountStyle.copy(fontSize = 12.sp),
                color = ExpenseCoral,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun TransactionRowCard(
  tx: TransactionRecord,
  lang: AppLang,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (icon, tint, bg, prefix) =
    when (tx.txType) {
      TransactionType.INCOME ->
        Quad(Icons.Default.ArrowDownward, IncomeGreen, IncomeGreenBg, "+")
      TransactionType.EXPENSE ->
        Quad(Icons.Default.ArrowUpward, ExpenseCoral, ExpenseCoralBg, "-")
      TransactionType.TRANSFER ->
        Quad(Icons.AutoMirrored.Filled.CompareArrows, TransferIndigo, TransferIndigoBg, "⇄ ")
    }

  val methodText =
    when (tx.method) {
      PaymentMethod.CASH -> AppStrings.cash(lang)
      PaymentMethod.BANK -> AppStrings.bank(lang)
      PaymentMethod.MOBILE_BANKING -> AppStrings.mobileBanking(lang)
    }

  val scopeText =
    if (tx.scope == AccountScope.PERSONAL) AppStrings.scopePersonal(lang)
    else AppStrings.scopeIsp(lang)

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("tx_card_${tx.id}")
        .clickable(onClick = onClick),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(bg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = tx.type, tint = tint, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = tx.partyName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${AppStrings.categoryLabel(lang, tx.category)} • $scopeText",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${tx.dateFormatted} • $methodText" + if (tx.referenceNumber.isNotBlank()) " • #${tx.referenceNumber}" else "",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "$prefix${Formatters.formatTaka(tx.amount)}",
        style = MonoAmountStyle.copy(fontSize = 15.sp),
        color = tint,
      )
    }
  }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun EmptyStateBanner(
  title: String,
  subtitle: String,
  icon: ImageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(38.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogModal(
  initialMillis: Long,
  onDismiss: () -> Unit,
  onDateSelected: (millis: Long, formatted: String) -> Unit,
) {
  val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val picked = state.selectedDateMillis ?: initialMillis
          onDateSelected(picked, Formatters.formatDate(picked))
        }
      ) {
        Text("OK")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  ) {
    DatePicker(state = state)
  }
}
