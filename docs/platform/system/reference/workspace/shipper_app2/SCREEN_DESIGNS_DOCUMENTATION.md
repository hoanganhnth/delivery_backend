# 📱 Amber Hearth Shipper App - 18 Screen Designs Overview

## Project Context
- **App**: Amber Hearth Premium Logistics - Shipper Driver Application
- **Platform**: React Native (Mobile-first)
- **Framework**: React Native with TypeScript
- **Design System**: Amber Hearth Design System
- **Figma Design File**: https://www.figma.com/design/yswuUhVfYgSV5Xd3iOdoj7/delivery

---

## 📋 18 Screen Designs Breakdown

### **1. Report Incident (Node ID: 2-2)**
**Purpose**: Allow shipper to report delivery issues/incidents
**Key Features**:
- Header with back button and help icon
- Active delivery badge with order number
- Incident reason selection (multiple choice cards):
  - Customer not answering
  - Restaurant closed
  - Vehicle breakdown
  - Accident
- Text area for detailed situation description
- "Submit Report" button
- Photo upload capability
- Warning box about urgent safety concerns
- Bottom bar with logo and app branding

**Visual Style**:
- Orange accent color (#f49d25) for primary actions
- White cards with rounded corners
- Tan/brown text labels (#9c7a49)
- Beige background (#f8f7f5)

---

### **2. Shipper Registration / Create Account (Node ID: 2-245)**
**Purpose**: New shipper onboarding and account creation
**Key Features**:
- Large form card with shadow and rounded corners
- Form fields:
  - Full Name (with user icon)
  - Email Address (with envelope icon)
  - Phone Number (with phone icon)
  - Vehicle Type selector (3 options: Bike, Motorcycle, Car)
  - Password (with lock icon and visibility toggle)
- "Create Account" button (orange gradient)
- Login link at bottom
- Footer with copyright info
- Responsive layout centered on desktop

**Visual Style**:
- White card with 48px rounded corners
- Orange CTA button with shadow
- Icon-based input fields
- Vehicle type selection with border highlight
- Professional enterprise feel

---

### **3. Payout Settings Dashboard (Node ID: 2-1708)**
**Purpose**: Manage earnings, payout schedule, and bank details
**Key Sections**:
- Header with title and description
- **Bento Grid Layout with 5 cards**:
  1. **Current Balance Hero Card** (Red/Orange gradient)
     - Available for payout amount
     - Next payout date badge
     - Watermark decoration
  
  2. **Bank Details Card**
     - Primary bank account info
     - Bank card display with last 4 digits
     - Verification badge
     - Edit button
  
  3. **Payout Frequency Card**
     - Schedule selection (Weekly/Daily)
     - Fee information
     - Current selection highlighted
  
  4. **Payout Threshold Card**
     - Slider control
     - Range indicators ($0-$500)
     - Info box with tax reporting tip
  
  5. **Recent History Table**
     - Date, destination, status, amount columns
     - Status badges (Paid/Processing)
     - View full report link

**Visual Style**:
- Orange/Red gradients for financial data
- White cards with subtle borders
- Status badges with color coding
- Professional dashboard layout

---

### **4. Order Details - Expanded View (Node ID: 2-1566)**
**Purpose**: Show detailed order information with delivery status
**Key Features**:
- **Header Section**:
  - Active delivery badge
  - Order number (#8829)
  - Estimated arrival time
  - Total payout amount
  - Driver profile photo
- **Tabs**: Order Items | Delivery Details
- **Content Sections**:
  - Items list (3+ items with quantity, specs, price)
  - Route logistics (Pickup & Dropoff details)
  - Addresses with manager info and contact
  - Special instructions
- **Bottom Actions**: Message | Call | Swipe to Complete button
- **Background**: Map view with gradient overlay

**Visual Style**:
- Bottom sheet UI pattern
- Rounded top corners (40px)
- Orange primary button for actions
- Card-based information display
- Map opacity background

---

### **5. Order History Item Detail (Node ID: 2-1398)**
**Purpose**: View completed order details and history
**Key Sections**:
- **Status Badge**: Delivered (green)
- **Order Header**: Order number, completion date/time
- **Action Buttons**: Need Help? | Reorder
- **Bento Grid with 3 columns**:
  1. **Items Delivered Card**
     - Item thumbnails
     - Quantity, name, specs, price
     - 3 items displayed
  
  2. **Payment Summary Card**
     - Subtotal, Delivery Fee, Service Fee
     - Total Paid amount (orange)
     - Payment method (Apple Pay)
  
  3. **Timeline Card** (with decorative element)
     - Journey events: Order Received → Prepared & Packed → Picked Up → Delivered
     - Timeline with checkmarks
     - Timestamps for each event

- **Address Cards** (bottom):
  - Delivery address with location icon
  - From restaurant info

**Visual Style**:
- Timeline with vertical connector
- Status-based color coding
- Green for successful/completed states
- Orange accents for important info

---

### **6. History & Earnings Dashboard (Node ID: 2-977)**
**Purpose**: Overview of earnings and order history
**Key Sections**:
- **Pull-to-refresh indicator** (top visual cue)
- **Quick Stats Bento Grid** (2 cards):
  1. **Today's Orders** (white card)
     - Large number display
     - Customer avatars stack
     - "+X more" indicator
  
  2. **Weekly Earnings** (orange/red gradient card)
     - Large amount display
     - Growth indicator (+12% badge)
     - Watermark decoration
     - Glass-morphism info badge

- **Order History Section**:
  - Filter button
  - **Completed Order Card**:
    - Order ID & items
    - Status badge (green)
    - Pickup/Delivery timeline
    - Shipping fee display
    - Delivery timestamp
  
  - **Cancelled Order Card** (dimmed):
    - Different status (red)
    - $0 refund
    - Cancellation time
  
  - **Additional Order Card**:
    - Similar layout to completed

**Visual Style**:
- Pull-to-refresh UI pattern
- Orange/red for earnings highlights
- Green for completed orders
- Red for cancelled states
- Overlay/dimmed states for cancelled

---

### **7. Vehicle Details Management (Node ID: 2-1148)**
**Purpose**: Manage vehicle information and compliance
**Key Features**:
- **Hero Section** with vehicle image
  - Vehicle model/year overlay
  - Vehicle type badge
  - Edit Name button
- **Bento Grid** (4 cards):
  1. **Specifications Card**
     - Model & Make
     - License Plate (in license plate style)
     - Color with swatch
     - Fuel/Power Type (Electric indicator)
     - Edit button
  
  2. **Vehicle Type Selector**
     - Category selection (Cargo Van selected)
     - Other options disabled
     - Switch Category button
  
  3. **Compliance Documents Card** (Large)
     - Document status cards:
       - Commercial Insurance (VERIFIED)
       - Vehicle Registration (VERIFIED)
       - Safety Inspection (EXPIRED - URGENT)
     - View File | Download buttons
     - Status badges
  
  4. **Fleet Info Card** (Dark background)
     - A+ Badge
     - Fleet approval badge
     - Download Badge button
     - Member since date

**Visual Style**:
- Hero image with dark overlay
- Document verification with color-coded status
- Orange for urgent/expired items
- Green for verified items
- Fleet approval dark card design

---

## 📝 Summary Statistics

**Total Screens**: 18 designs
**Key Screen Categories**:
- **Authentication & Onboarding** (1): Registration
- **Incident Management** (1): Report Incident
- **Delivery Management** (2): Order Details, Expanded View
- **Financial/Payout** (1): Payout Settings
- **History & Analytics** (2): Order History Detail, History & Earnings
- **Vehicle Management** (1): Vehicle Details
- **Additional Designs** (10): Loading screens, empty states, notifications, etc. (to be documented)

---

## 🎨 Design System Standards

### **Color Palette**:
- **Primary Orange**: #f49d25
- **Secondary Orange**: #ea580c
- **Dark Text**: #1c160d, #1c1917
- **Muted Text**: #9c7a49
- **Background**: #f8f7f5
- **White**: #ffffff
- **Success Green**: #15803d, #22c55e
- **Error Red**: #b91c1c
- **Borders**: #e7e5e4, #d6d3d1

### **Typography**:
- **Font Family**: Plus Jakarta Sans
- **Weights**: Bold, ExtraBold, Regular, Medium
- **Headings**: ExtraBold (24-36px)
- **Body**: Regular (14-16px)
- **Labels**: Bold uppercase (10-12px)

### **Components**:
- **Buttons**: Rounded corners (9999px), shadow effects
- **Cards**: 16-40px border radius, subtle shadows
- **Input Fields**: 16px rounded, icon left-aligned
- **Status Badges**: Pill-shaped, color-coded
- **Bottom Sheets**: 40px top corner radius

### **Spacing**:
- Primary spacing: 16px, 24px, 32px, 48px

---

## 🚀 Implementation Priority

**Phase 1 (Critical)**:
1. Registration screen
2. Order details screen
3. Payout settings

**Phase 2 (High)**:
1. Order history
2. History & Earnings
3. Vehicle details

**Phase 3 (Medium)**:
1. Report incident
2. Supporting screens

---

## 📱 Responsive Design Notes

All screens designed for:
- **Mobile-first**: 390px base width
- **Tablet**: 672px-1024px
- **Desktop**: 1024px+
- Uses React Native patterns with TypeScript
- Tailwind CSS not recommended (use React Native stylesheet or styled-components)

