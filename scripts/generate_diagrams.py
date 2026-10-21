import matplotlib.pyplot as plt
import matplotlib.patches as patches

def generate_architecture_diagram():
    fig, ax = plt.subplots(figsize=(12, 7), dpi=300)
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 8)
    ax.axis('off')

    # Background color
    fig.patch.set_facecolor('#ffffff')

    # Color palette
    c_client = '#e0f2fe'  # Sky light
    c_edge = '#fef3c7'    # Amber light
    c_service = '#f0fdf4' # Emerald light
    c_data = '#fdf4ff'    # Purple light
    c_border = '#334155'

    # Title
    ax.text(6, 7.6, "SCALABLE HOTEL MANAGEMENT SYSTEM ARCHITECTURE", 
            ha='center', va='center', fontsize=14, fontweight='bold', color='#0f172a')
    ax.text(6, 7.25, "Distributed Multi-Tier Topology with Linearizable Invariant Protection", 
            ha='center', va='center', fontsize=10, fontstyle='italic', color='#475569')

    # Tier 1: Client Tier
    rect_client = patches.FancyBboxPatch((0.5, 5.0), 2.5, 1.8, boxstyle="round,pad=0.1", 
                                        ec='#0284c7', fc=c_client, lw=1.5)
    ax.add_patch(rect_client)
    ax.text(1.75, 6.4, "CLIENT TIER", ha='center', va='center', fontsize=11, fontweight='bold', color='#0369a1')
    ax.text(1.75, 5.9, "• React 19 SPA (Vite)\n• Guest Portal\n• Reception Desk\n• Housekeeping Kanban\n• Telemetry Dashboard", 
            ha='center', va='center', fontsize=8, color='#0f172a')

    # Arrow Client -> Gateway
    ax.annotate('', xy=(3.8, 5.9), xytext=(3.0, 5.9),
                arrowprops=dict(arrowstyle="<->", color='#0284c7', lw=2))
    ax.text(3.4, 6.15, "REST / JSON\nJWT Bearer", ha='center', va='center', fontsize=7, color='#0369a1')

    # Tier 2: Edge & Rate Limiting
    rect_edge = patches.FancyBboxPatch((3.8, 5.0), 2.4, 1.8, boxstyle="round,pad=0.1", 
                                       ec='#d97706', fc=c_edge, lw=1.5)
    ax.add_patch(rect_edge)
    ax.text(5.0, 6.4, "EDGE / SECURITY", ha='center', va='center', fontsize=11, fontweight='bold', color='#b45309')
    ax.text(5.0, 5.9, "• Token Bucket Rate Limiter\n• Spring Security Filter\n• HMAC-SHA512 JWT Auth\n• Base62 Redirect Controller", 
            ha='center', va='center', fontsize=8, color='#0f172a')

    # Arrow Edge -> Core Services
    ax.annotate('', xy=(6.9, 5.9), xytext=(6.2, 5.9),
                arrowprops=dict(arrowstyle="->", color='#d97706', lw=2))

    # Tier 3: Core Application Services
    rect_srv = patches.FancyBboxPatch((6.9, 4.3), 4.6, 2.5, boxstyle="round,pad=0.1", 
                                      ec='#16a34a', fc=c_service, lw=1.5)
    ax.add_patch(rect_srv)
    ax.text(9.2, 6.5, "CORE APPLICATION MICROSERVICE TIER (Java 21 / Spring Boot)", 
            ha='center', va='center', fontsize=10, fontweight='bold', color='#15803d')
    ax.text(8.0, 5.5, "• BookingService (Distributed Lock)\n• RoomService (Cache-Aside)\n• BillingService (12% GST)\n• HousekeepingService (Kanban)", 
            ha='left', va='center', fontsize=8, color='#0f172a')
    ax.text(8.0, 4.7, "• SnowflakeIdGenerator (64-bit)\n• ConsistentHashRing (100 VNodes)\n• ExternalHotelCrawler (JSoup)\n• AutocompleteService (ZSet/Trie)", 
            ha='left', va='center', fontsize=8, color='#0f172a')

    # Arrows Core -> Data Tier
    ax.annotate('', xy=(5.0, 2.8), xytext=(7.5, 4.3),
                arrowprops=dict(arrowstyle="<->", color='#0d9488', lw=2))
    ax.text(6.0, 3.6, "Distributed Lock\nZSet Autocomplete\nKey-Value Cache", 
            ha='center', va='center', fontsize=7, color='#0f766e')

    ax.annotate('', xy=(9.2, 2.8), xytext=(9.2, 4.3),
                arrowprops=dict(arrowstyle="<->", color='#9333ea', lw=2))
    ax.text(9.5, 3.6, "Document Persistence\nACID Invariant Validation\nShard Routing", 
            ha='left', va='center', fontsize=7, color='#7e22ce')

    # Tier 4A: Redis Infrastructure
    rect_redis = patches.FancyBboxPatch((3.5, 1.0), 3.0, 1.8, boxstyle="round,pad=0.1", 
                                        ec='#0d9488', fc='#ccfbf1', lw=1.5)
    ax.add_patch(rect_redis)
    ax.text(5.0, 2.5, "REDIS IN-MEMORY STORE", ha='center', va='center', fontsize=11, fontweight='bold', color='#0f766e')
    ax.text(5.0, 1.8, "• Redis Lock (Spin-lock Mutex)\n• Token Bucket State (Lua script)\n• Sorted Sets (Lex Autocomplete)\n• Cached Room & Hotel Catalog", 
            ha='center', va='center', fontsize=8, color='#0f172a')

    # Tier 4B: MongoDB Storage Tier
    rect_mongo = patches.FancyBboxPatch((7.7, 1.0), 3.8, 1.8, boxstyle="round,pad=0.1", 
                                        ec='#9333ea', fc=c_data, lw=1.5)
    ax.add_patch(rect_mongo)
    ax.text(9.6, 2.5, "MONGODB PERSISTENCE TIER", ha='center', va='center', fontsize=11, fontweight='bold', color='#7e22ce')
    ax.text(9.6, 1.8, "• Primary Collections (users, hotels, rooms)\n• Transactional Bookings (Compound Indexes)\n• Shard Logical Namespaces (shard_0, shard_1)\n• Invoices, Notifications, Short URLs", 
            ha='center', va='center', fontsize=8, color='#0f172a')

    plt.tight_layout()
    plt.savefig("/home/shiva/SDLAB/report/figures/architecture_diagram.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("architecture_diagram.png generated!")

def generate_er_diagram():
    fig, ax = plt.subplots(figsize=(12, 7.5), dpi=300)
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 8)
    ax.axis('off')
    fig.patch.set_facecolor('#ffffff')

    ax.text(6, 7.6, "DATABASE ENTITY-RELATIONSHIP SCHEMA", 
            ha='center', va='center', fontsize=14, fontweight='bold', color='#0f172a')
    ax.text(6, 7.25, "MongoDB Document Collections with Compound Index Constraints", 
            ha='center', va='center', fontsize=10, fontstyle='italic', color='#475569')

    # User Entity
    p_user = patches.FancyBboxPatch((0.5, 4.2), 2.8, 2.4, boxstyle="round,pad=0.1", ec='#2563eb', fc='#eff6ff', lw=1.5)
    ax.add_patch(p_user)
    ax.text(1.9, 6.3, "USERS", ha='center', va='center', fontsize=11, fontweight='bold', color='#1d4ed8')
    ax.text(0.7, 5.2, "• id : String (PK)\n• email : String (Unique Index)\n• passwordHash : String (BCrypt)\n• fullName : String\n• role : Enum [GUEST, ADMIN,\n   RECEPTIONIST, HOUSEKEEPING]\n• phone : String", 
            ha='left', va='center', fontsize=8, color='#1e293b')

    # Hotel Entity
    p_hotel = patches.FancyBboxPatch((4.5, 4.4), 2.8, 2.2, boxstyle="round,pad=0.1", ec='#0d9488', fc='#f0fdfa', lw=1.5)
    ax.add_patch(p_hotel)
    ax.text(5.9, 6.3, "HOTELS", ha='center', va='center', fontsize=11, fontweight='bold', color='#0f766e')
    ax.text(4.7, 5.3, "• id : String (PK)\n• name : String (Index)\n• city : String (Index)\n• address : String\n• amenities : List<String>\n• rating : Double", 
            ha='left', va='center', fontsize=8, color='#1e293b')

    # Room Entity
    p_room = patches.FancyBboxPatch((8.5, 4.2), 3.0, 2.4, boxstyle="round,pad=0.1", ec='#d97706', fc='#fffbeb', lw=1.5)
    ax.add_patch(p_room)
    ax.text(10.0, 6.3, "ROOMS", ha='center', va='center', fontsize=11, fontweight='bold', color='#b45309')
    ax.text(8.7, 5.2, "• id : String (PK)\n• hotelId : String (FK -> Hotels)\n• roomNumber : String (Index)\n• type : Enum [SINGLE, DOUBLE,\n   SUITE, DELUXE]\n• pricePerNight : Double\n• housekeepingStatus : Enum", 
            ha='left', va='center', fontsize=8, color='#1e293b')

    # Booking Entity
    p_booking = patches.FancyBboxPatch((4.2, 0.8), 3.4, 2.8, boxstyle="round,pad=0.1", ec='#dc2626', fc='#fef2f2', lw=1.5)
    ax.add_patch(p_booking)
    ax.text(5.9, 3.3, "BOOKINGS (Core Invariant)", ha='center', va='center', fontsize=11, fontweight='bold', color='#b91c1c')
    ax.text(4.4, 2.0, "• id : String (PK, Snowflake 64-bit)\n• guestId : String (FK -> Users)\n• roomId : String (FK -> Rooms)\n• checkInDate : LocalDate (Compound Index)\n• checkOutDate : LocalDate (Compound Index)\n• status : Enum [CONFIRMED, CHECKED_IN,\n   CHECKED_OUT, CANCELLED]\n• totalPrice : Double\n• idempotencyKey : String (Unique Index)", 
            ha='left', va='center', fontsize=8, color='#1e293b')

    # Invoice Entity
    p_invoice = patches.FancyBboxPatch((0.5, 0.8), 3.0, 2.6, boxstyle="round,pad=0.1", ec='#7c3aed', fc='#faf5ff', lw=1.5)
    ax.add_patch(p_invoice)
    ax.text(2.0, 3.1, "INVOICES", ha='center', va='center', fontsize=11, fontweight='bold', color='#6d28d9')
    ax.text(0.7, 2.0, "• id : String (PK)\n• invoiceId : String (Snowflake ID)\n• bookingId : String (FK -> Bookings)\n• guestEmail : String\n• roomCharges, taxAmount (12%)\n• serviceFee, totalAmount\n• shortUrl : String (Base62)", 
            ha='left', va='center', fontsize=8, color='#1e293b')

    # Relationships
    # Users -> Bookings (1:N)
    ax.annotate('', xy=(4.2, 2.2), xytext=(2.5, 4.2),
                arrowprops=dict(arrowstyle="->", color='#2563eb', lw=1.5))
    ax.text(3.0, 3.3, "1 : N (guestId)", ha='center', va='center', fontsize=7, color='#1d4ed8')

    # Hotels -> Rooms (1:N)
    ax.annotate('', xy=(8.5, 5.4), xytext=(7.3, 5.4),
                arrowprops=dict(arrowstyle="->", color='#0d9488', lw=1.5))
    ax.text(7.9, 5.65, "1 : N (hotelId)", ha='center', va='center', fontsize=7, color='#0f766e')

    # Rooms -> Bookings (1:N)
    ax.annotate('', xy=(7.6, 2.4), xytext=(9.2, 4.2),
                arrowprops=dict(arrowstyle="->", color='#d97706', lw=1.5))
    ax.text(8.8, 3.3, "1 : N (roomId)", ha='center', va='center', fontsize=7, color='#b45309')

    # Bookings -> Invoices (1:1)
    ax.annotate('', xy=(3.5, 2.0), xytext=(4.2, 2.0),
                arrowprops=dict(arrowstyle="<-", color='#7c3aed', lw=1.5))
    ax.text(3.85, 2.2, "1 : 1", ha='center', va='center', fontsize=7, color='#6d28d9')

    plt.tight_layout()
    plt.savefig("/home/shiva/SDLAB/report/figures/er_diagram.png", dpi=300, bbox_inches='tight')
    plt.close()
    print("er_diagram.png generated!")

if __name__ == "__main__":
    generate_architecture_diagram()
    generate_er_diagram()
