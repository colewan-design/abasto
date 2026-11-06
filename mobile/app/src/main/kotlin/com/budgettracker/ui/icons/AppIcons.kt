package com.budgettracker.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.budgettracker.data.model.AccountType

/**
 * The server names icons in the same vocabulary the web client uses (Lucide's
 * kebab-case names), because both clients read the same category catalog. This
 * is the one place that vocabulary is translated into Material's set.
 *
 * An unknown name falls back to a neutral icon rather than failing — the
 * catalog can grow on the server without shipping an app update.
 */
object AppIcons {

    private val byName: Map<String, ImageVector> = mapOf(
        "baby" to Icons.Outlined.ChildCare,
        "badge-percent" to Icons.Outlined.LocalOffer,
        "bank" to Icons.Outlined.AccountBalance,
        "bolt" to Icons.Outlined.Bolt,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "briefcase" to Icons.Outlined.Work,
        "building" to Icons.Outlined.Apartment,
        "car" to Icons.Outlined.DirectionsCar,
        "certificate" to Icons.Outlined.WorkspacePremium,
        "chart-pie" to Icons.Outlined.PieChart,
        "circle-ellipsis" to Icons.Outlined.MoreHoriz,
        "circle-plus" to Icons.Outlined.AddCircleOutline,
        "coffee" to Icons.Outlined.LocalCafe,
        "credit-card" to Icons.Outlined.CreditCard,
        "dumbbell" to Icons.Outlined.FitnessCenter,
        "file-text" to Icons.Outlined.Description,
        "fuel" to Icons.Outlined.LocalGasStation,
        "gamepad" to Icons.Outlined.SportsEsports,
        "gift" to Icons.Outlined.Redeem,
        "graduation-cap" to Icons.Outlined.School,
        "hand-coins" to Icons.Outlined.Savings,
        "heart-handshake" to Icons.Outlined.VolunteerActivism,
        "heart-pulse" to Icons.Outlined.MonitorHeart,
        "home" to Icons.Outlined.Home,
        "key" to Icons.Outlined.VpnKey,
        "landmark" to Icons.Outlined.AccountBalance,
        "laptop" to Icons.Outlined.Laptop,
        "moped" to Icons.Outlined.TwoWheeler,
        "parking" to Icons.Outlined.LocalParking,
        "party-popper" to Icons.Outlined.Celebration,
        "paw-print" to Icons.Outlined.Pets,
        "percent" to Icons.Outlined.Percent,
        "phone" to Icons.Outlined.Smartphone,
        "piggy-bank" to Icons.Outlined.Savings,
        "pill" to Icons.Outlined.Medication,
        "plane" to Icons.Outlined.Flight,
        "receipt" to Icons.AutoMirrored.Outlined.ReceiptLong,
        "repeat" to Icons.Outlined.Repeat,
        "rotate-ccw" to Icons.Outlined.History,
        "school" to Icons.Outlined.School,
        "scissors" to Icons.Outlined.ContentCut,
        "shield" to Icons.Outlined.Shield,
        "shirt" to Icons.Outlined.Checkroom,
        "shopping-bag" to Icons.Outlined.ShoppingBag,
        "shopping-cart" to Icons.Outlined.ShoppingCart,
        "sofa" to Icons.Outlined.Weekend,
        "sparkles" to Icons.Outlined.AutoAwesome,
        "spray-bottle" to Icons.Outlined.CleaningServices,
        "stethoscope" to Icons.Outlined.MedicalServices,
        "store" to Icons.Outlined.Storefront,
        "ticket" to Icons.Outlined.ConfirmationNumber,
        "train" to Icons.Outlined.Train,
        "trending-up" to Icons.AutoMirrored.Outlined.TrendingUp,
        "user" to Icons.Outlined.Person,
        "utensils" to Icons.Outlined.Restaurant,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "wifi" to Icons.Outlined.Wifi,
        "wrench" to Icons.Outlined.Build,
    )

    val fallback: ImageVector = Icons.Outlined.Category

    fun named(name: String?): ImageVector =
        name?.lowercase()?.let(byName::get) ?: fallback

    fun forAccount(type: AccountType): ImageVector = when (type) {
        AccountType.Cash -> Icons.Outlined.AccountBalanceWallet
        AccountType.Bank -> Icons.Outlined.AccountBalance
        AccountType.Ewallet -> Icons.Outlined.Smartphone
        AccountType.CreditCard -> Icons.Outlined.CreditCard
        AccountType.Savings -> Icons.Outlined.Savings
    }
}
