package work.spyglee.ladger.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons a category, source or collection can use. The keys are the iOS SF Symbol names, which is
 * what both apps store, in the same order as the iOS icon picker (`models/Icons.swift`).
 */
val pickableIcons: Map<String, ImageVector> = linkedMapOf(
    "tag" to Icons.Outlined.LocalOffer,
    "cart" to Icons.Outlined.ShoppingCart,
    "basket" to Icons.Outlined.ShoppingBasket,
    "bag" to Icons.Outlined.ShoppingBag,
    "creditcard" to Icons.Outlined.CreditCard,
    "banknote" to Icons.Outlined.Payments,
    "wallet.pass" to Icons.Outlined.AccountBalanceWallet,
    "dollarsign.circle" to Icons.Outlined.MonetizationOn,
    "building.columns" to Icons.Outlined.AccountBalance,
    "chart.line.uptrend.xyaxis" to Icons.AutoMirrored.Outlined.TrendingUp,
    "fork.knife" to Icons.Outlined.Restaurant,
    "cup.and.saucer" to Icons.Outlined.LocalCafe,
    "takeoutbag.and.cup.and.straw" to Icons.Outlined.Fastfood,
    "carrot" to Icons.Outlined.Grass,
    "wineglass" to Icons.Outlined.WineBar,
    "birthday.cake" to Icons.Outlined.Cake,
    "popcorn" to Icons.Outlined.Theaters,
    "house" to Icons.Outlined.Home,
    "bed.double" to Icons.Outlined.Bed,
    "sofa" to Icons.Outlined.Weekend,
    "lightbulb" to Icons.Outlined.Lightbulb,
    "drop" to Icons.Outlined.WaterDrop,
    "flame" to Icons.Outlined.LocalFireDepartment,
    "bolt" to Icons.Outlined.Bolt,
    "wifi" to Icons.Outlined.Wifi,
    "antenna.radiowaves.left.and.right" to Icons.Outlined.SettingsInputAntenna,
    "trash" to Icons.Outlined.Delete,
    "car" to Icons.Outlined.DirectionsCar,
    "fuelpump" to Icons.Outlined.LocalGasStation,
    "bus" to Icons.Outlined.DirectionsBus,
    "tram" to Icons.Outlined.Tram,
    "bicycle" to Icons.Outlined.PedalBike,
    "airplane" to Icons.Outlined.Flight,
    "ferry" to Icons.Outlined.DirectionsBoat,
    "figure.walk" to Icons.AutoMirrored.Outlined.DirectionsWalk,
    "cross.case" to Icons.Outlined.MedicalServices,
    "pills" to Icons.Outlined.Medication,
    "stethoscope" to Icons.Outlined.LocalHospital,
    "heart" to Icons.Outlined.Favorite,
    "dumbbell" to Icons.Outlined.FitnessCenter,
    "figure.run" to Icons.AutoMirrored.Outlined.DirectionsRun,
    "sportscourt" to Icons.Outlined.SportsSoccer,
    "book" to Icons.Outlined.Book,
    "graduationcap" to Icons.Outlined.School,
    "pencil" to Icons.Outlined.Edit,
    "paintbrush" to Icons.Outlined.Brush,
    "theatermasks" to Icons.Outlined.TheaterComedy,
    "camera" to Icons.Outlined.PhotoCamera,
    "film" to Icons.Outlined.Movie,
    "music.note" to Icons.Outlined.MusicNote,
    "headphones" to Icons.Outlined.Headphones,
    "gamecontroller" to Icons.Outlined.SportsEsports,
    "ticket" to Icons.Outlined.ConfirmationNumber,
    "tv" to Icons.Outlined.Tv,
    "iphone" to Icons.Outlined.Smartphone,
    "laptopcomputer" to Icons.Outlined.Laptop,
    "printer" to Icons.Outlined.Print,
    "gift" to Icons.Outlined.CardGiftcard,
    "tshirt" to Icons.Outlined.Checkroom,
    "shoe" to Icons.Outlined.Hiking,
    "scissors" to Icons.Outlined.ContentCut,
    "comb" to Icons.Outlined.Face,
    "sparkles" to Icons.Outlined.AutoAwesome,
    "pawprint" to Icons.Outlined.Pets,
    "leaf" to Icons.Outlined.Eco,
    "tree" to Icons.Outlined.Park,
    "hammer" to Icons.Outlined.Construction,
    "wrench.and.screwdriver" to Icons.Outlined.Build,
    "shippingbox" to Icons.Outlined.Inventory2,
    "briefcase" to Icons.Outlined.Work,
    "suitcase" to Icons.Outlined.Luggage,
    "globe" to Icons.Outlined.Public,
    "map" to Icons.Outlined.Map,
    "phone" to Icons.Outlined.Phone,
    "envelope" to Icons.Outlined.Mail,
    "person.2" to Icons.Outlined.People,
    "figure.2.and.child.holdinghands" to Icons.Outlined.FamilyRestroom,
    "star" to Icons.Outlined.Star,
    "questionmark.circle" to Icons.AutoMirrored.Outlined.HelpOutline,
)

/** The icon for a stored SF Symbol name; names the picker doesn't offer fall back to a tag. */
fun iconFor(systemImage: String): ImageVector = pickableIcons[systemImage] ?: Icons.Outlined.LocalOffer
