# React Native Component Implementation Guide

## Overview
This guide explains the 4 converted React Native components from the Figma designs and how to integrate them into the shipper_app project.

## Implemented Components

### 1. DeliverySuccess
**File:** `src/presentation/screens/delivery/DeliverySuccess.tsx`  
**Purpose:** Success screen shown after delivery completion

#### Usage:
```tsx
import DeliverySuccess from '../screens/delivery/DeliverySuccess';

<DeliverySuccess
  onBackToMap={() => navigation.goBack()}
  onViewSummary={() => navigation.navigate('Summary')}
  tripEarnings={24.50}
  tipAmount={5.00}
  timeTaken="22 Minutes"
  customerName="Sarah J."
  completedDeliveries={15}
  progressPercentage={84}
/>
```

#### Props:
- `onBackToMap?: () => void` - Back button callback
- `onViewSummary?: () => void` - View summary button callback
- `tripEarnings?: number` - Trip earnings amount (default: 24.50)
- `tipAmount?: number` - Tip amount (default: 5.00)
- `timeTaken?: string` - Time spent on delivery (default: "22 Minutes")
- `customerName?: string` - Customer name (default: "Sarah J.")
- `completedDeliveries?: number` - Weekly deliveries (default: 15)
- `progressPercentage?: number` - Progress to goal (default: 84)

#### Features:
- Celebration icon with gradient background
- Earnings display with tip badge
- Stats grid (time taken, customer)
- Milestone achievement card
- Primary and secondary action buttons
- Proper spacing and visual hierarchy

---

### 2. ShipperProfile
**File:** `src/presentation/screens/profile/ShipperProfile.tsx`  
**Purpose:** Shipper profile with account management menu

#### Usage:
```tsx
import ShipperProfile from '../screens/profile/ShipperProfile';

<ShipperProfile
  onLogout={handleLogout}
  onPersonalInfoPress={() => navigation.navigate('PersonalInfo')}
  onVehicleDetailsPress={() => navigation.navigate('VehicleDetails')}
  onPayoutSettingsPress={() => navigation.navigate('PayoutSettings')}
  onDocumentsPress={() => navigation.navigate('Documents')}
  shipper={{
    name: 'Marcus Vane',
    rating: 4.95,
    earnings: 12840,
    ordersCount: 1402,
    isElite: true,
  }}
/>
```

#### Props:
- `onLogout?: () => void` - Logout button callback
- `onPersonalInfoPress?: () => void` - Personal info menu item callback
- `onVehicleDetailsPress?: () => void` - Vehicle details menu item callback
- `onPayoutSettingsPress?: () => void` - Payout settings menu item callback
- `onDocumentsPress?: () => void` - Documents menu item callback
- `shipper?: { name, rating, earnings, ordersCount, isElite }` - Shipper data

#### Features:
- Profile hero with avatar and elite badge
- Stats grid showing earnings, orders, rating
- Account management menu (4 items)
- Status indicator on avatar
- Logout button with custom styling
- Header with menu button

#### Integration with Redux:
```tsx
const shipper = useSelector((state: RootState) => state.shipper.data);

<ShipperProfile
  shipper={{
    name: shipper?.name || '',
    rating: shipper?.rating || 0,
    earnings: shipper?.earnings || 0,
    ordersCount: shipper?.ordersCount || 0,
    isElite: shipper?.isElite || false,
  }}
/>
```

---

### 3. ShipperRegistration
**File:** `src/presentation/screens/auth/ShipperRegistration.tsx`  
**Purpose:** Account creation form for new shippers

#### Usage:
```tsx
import ShipperRegistration from '../screens/auth/ShipperRegistration';

<ShipperRegistration
  onCreateAccount={(data) => {
    console.log('Create account:', data);
    // Call your registration API
  }}
  onLoginLink={() => navigation.navigate('Login')}
/>
```

#### Props:
- `onCreateAccount?: (data: RegistrationData) => void` - Form submission callback
- `onLoginLink?: () => void` - Login link callback

#### RegistrationData Structure:
```tsx
{
  fullName: string;      // e.g., "Marcus Vane"
  email: string;         // e.g., "marcus@shipper.com"
  phone: string;         // e.g., "+1 (555) 000-0000"
  vehicleType: 'bike' | 'motorcycle' | 'car' | null;
  password: string;      // Encrypted password
}
```

#### Features:
- Full name input with icon
- Email input with validation-ready
- Phone number input
- Vehicle type selector (3 options: bike, motorcycle, car)
- Password input with show/hide toggle
- Create account button
- Footer with login link
- Form validation-ready structure
- Keyboard avoiding for better UX

#### Form Validation Example:
```tsx
const handleCreateAccount = (data) => {
  if (!data.fullName?.trim()) {
    showError('Please enter your full name');
    return;
  }
  if (!data.email?.includes('@')) {
    showError('Please enter a valid email');
    return;
  }
  if (!data.phone?.trim()) {
    showError('Please enter your phone number');
    return;
  }
  if (!data.vehicleType) {
    showError('Please select a vehicle type');
    return;
  }
  if (data.password?.length < 8) {
    showError('Password must be at least 8 characters');
    return;
  }
  // Proceed with registration
};
```

---

### 4. PermissionsRequest
**File:** `src/presentation/screens/splash/PermissionsRequest.tsx`  
**Purpose:** Onboarding permissions request screen

#### Usage:
```tsx
import PermissionsRequest from '../screens/splash/PermissionsRequest';
import { PermissionsAndroid } from 'react-native';

<PermissionsRequest
  onAllowAll={async () => {
    try {
      const gpsGranted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
      const notifGranted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
      );
      if (gpsGranted && notifGranted) {
        navigation.navigate('MainMap');
      }
    } catch (err) {
      console.error(err);
    }
  }}
  onLater={() => {
    // Skip for now, but don't forget to request later
    navigation.navigate('MainMap');
  }}
/>
```

#### Props:
- `onAllowAll?: () => void` - Allow all permissions button callback
- `onLater?: () => void` - Later button callback

#### Features:
- Eye-catching hero title with emphasized "Success"
- GPS permission card with detailed description
- Push notifications permission card
- Visual placeholders for future image assets
- Two-action button layout
- Privacy compliance text
- Smooth scrolling for all screen sizes

#### iOS Permissions (Info.plist):
```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need your location to match you with nearby delivery orders and provide accurate ETAs</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>We need continuous location access to track your deliveries and optimize routes</string>

<key>NSLocationAlwaysUsageDescription</key>
<string>We need continuous location access to track your deliveries</string>

<key>NSUserNotificationsUsageDescription</key>
<string>We'll send you alerts for new orders and delivery updates</string>
```

#### Android Permissions (AndroidManifest.xml):
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Styling & Design System

### Color Constants
All components use color constants from `src/utils/constants/color.js`:

```ts
export const PRIMARY = {
  ORANGE: '#f49d25',
  ORANGE_SECONDARY: '#f97316',
  ORANGE_LIGHT: '#ffedd5',
};

export const BACKGROUND = {
  PRIMARY: '#FFFFFF',
  LIGHT: '#f8f7f5',
  LIGHTER: '#fafaf9',
};

export const TEXT = {
  PRIMARY: '#1c160d',
  SECONDARY: '#1c1917',
  MUTED: '#9c7a49',
};
```

### Typography Standards
- **Extra Bold (800):** Headings, titles
- **Bold (700):** Labels, section headers
- **Medium (500):** Subtitles
- **Regular (400):** Body text, descriptions

### Common Size Values
- **Border Radius:** 9999 (pills), 40, 32, 24, 16, 12
- **Shadow Styles:** Consistently applied for depth
- **Spacing:** 8, 12, 16, 20, 24, 32, 48

---

## Navigation Integration

### Add to Navigation Stack:
```tsx
import DeliverySuccess from '../screens/delivery/DeliverySuccess';
import ShipperProfile from '../screens/profile/ShipperProfile';
import ShipperRegistration from '../screens/auth/ShipperRegistration';
import PermissionsRequest from '../screens/splash/PermissionsRequest';

export const AppNavigator = () => {
  return (
    <Stack.Navigator>
      {/* Auth Stack */}
      <Stack.Screen
        name="Register"
        component={ShipperRegistration}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="PermissionsRequest"
        component={PermissionsRequest}
        options={{ headerShown: false }}
      />
      
      {/* Main Stack */}
      <Stack.Screen
        name="Profile"
        component={ShipperProfile}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="DeliverySuccess"
        component={DeliverySuccess}
        options={{ headerShown: false }}
      />
    </Stack.Navigator>
  );
};
```

---

## Testing Components

### Local Testing:
```tsx
// App.tsx or test file
import React, { useState } from 'react';
import DeliverySuccess from './src/presentation/screens/delivery/DeliverySuccess';

export default function App() {
  return (
    <DeliverySuccess
      onBackToMap={() => console.log('Back to map')}
      onViewSummary={() => console.log('View summary')}
    />
  );
}
```

### Component Props Testing:
```tsx
// Test with different props
<DeliverySuccess
  tripEarnings={99.99}
  progressPercentage={50}
  customerName="John Doe"
/>

<ShipperProfile
  shipper={{
    name: "Test User",
    rating: 3.5,
    earnings: 5000,
    ordersCount: 100,
    isElite: false,
  }}
/>
```

---

## Redux Integration Example

### Connect to Redux Store:
```tsx
import { useSelector, useDispatch } from 'react-redux';
import { RootState, AppDispatch } from '../redux/store';

const MyComponent = () => {
  const shipper = useSelector((state: RootState) => state.shipper.data);
  const dispatch = useDispatch<AppDispatch>();

  return (
    <ShipperProfile
      shipper={shipper}
      onLogout={() => {
        dispatch(logout());
      }}
    />
  );
};
```

---

## Common Patterns

### Button Callbacks:
```tsx
// All components follow this pattern
onPress={() => {
  // Do something
  navigation.navigate('NextScreen');
}}
```

### Error Handling:
```tsx
try {
  // Call API
} catch (error) {
  // Show error to user
  console.error('Error:', error);
}
```

### Loading States:
```tsx
const [loading, setLoading] = useState(false);

const handleSubmit = async () => {
  setLoading(true);
  try {
    // Do work
  } finally {
    setLoading(false);
  }
};
```

---

## Performance Optimization

All components use:
- ✅ `StyleSheet.create()` for optimized styles
- ✅ `React.memo` ready (can be wrapped if needed)
- ✅ Proper `key` props in lists
- ✅ Minimal re-renders
- ✅ ScrollView for long content

---

## Next Components to Implement

1. **PermissionsRequest** ✅ (Implemented)
2. **ForgotPasswordShipper** - Password recovery form
3. **MainMapOrderRequest** - Order acceptance with map
4. **Notifications** - Activity center
5. **PayoutSettings** - Payment configuration

See `DESIGN_IMPLEMENTATION_STATUS.md` for full roadmap.

---

## Support & Resources

- **Color System:** `src/utils/constants/color.js`
- **Existing Components:** `src/presentation/components/`
- **Redux Store:** `src/presentation/redux/store.ts`
- **Navigation:** `src/navigation/AppNavigator.tsx`
- **Type Definitions:** `src/types/`

**Questions?** Reference the original Figma designs for visual details or the implementation guide at `src/presentation/screens/DESIGN_IMPLEMENTATION_GUIDE.md`
