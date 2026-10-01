import SwiftUI
import CoreBluetooth
import PhotosUI

/**
 * Ollo AR Glasses iOS Companion Host App
 * Provides Deck Management, 1-bit Image Pipeline, and BLE Synchronization for iOS.
 */
struct ContentView: View {
    @StateObject private var ble = IOSBleManager()
    @State private var cards: [IOSCard] = [
        IOSCard(id: 1, frontText: "Welcome to Ollo!", backText: "DIY AR Glasses for passive learning", frontImgId: 0, backImgId: 0)
    ]
    @State private var showingEditSheet = false
    @State private var editingCard: IOSCard? = nil
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            // Deck Tab
            NavigationView {
                ZStack {
                    Color(red: 0.04, green: 0.05, blue: 0.07).ignoresSafeArea()
                    
                    VStack(spacing: 0) {
                        // Storage Budget Banner
                        storageBanner
                        
                        // Cards List
                        if cards.isEmpty {
                            VStack(spacing: 12) {
                                Spacer()
                                Image(systemName: "eyeglasses")
                                    .font(.system(size: 48))
                                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                                Text("No cards in deck")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("Tap '+' to create your first flashcard")
                                    .font(.subheadline)
                                    .foregroundColor(.gray)
                                Spacer()
                            }
                        } else {
                            List {
                                ForEach(cards) { card in
                                    CardRowView(card: card)
                                        .listRowBackground(Color(red: 0.08, green: 0.10, blue: 0.13))
                                        .onTapGesture {
                                            editingCard = card
                                            showingEditSheet = true
                                        }
                                }
                                .onDelete { indexSet in
                                    cards.remove(atOffsets: indexSet)
                                }
                            }
                            .listStyle(.plain)
                        }
                    }
                }
                .navigationTitle("Ollo Deck")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: {
                            editingCard = IOSCard(id: Int.random(in: 100...99999), frontText: "", backText: "", frontImgId: 0, backImgId: 0)
                            showingEditSheet = true
                        }) {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                        }
                    }
                }
            }
            .tabItem {
                Label("Deck", systemImage: "rectangle.stack.fill")
            }
            .tag(0)

            // BLE Sync Tab
            NavigationView {
                ZStack {
                    Color(red: 0.04, green: 0.05, blue: 0.07).ignoresSafeArea()
                    SyncView(ble: ble, cards: cards)
                }
                .navigationTitle("BLE Sync")
            }
            .tabItem {
                Label("Sync", systemImage: "arrow.triangle.2.circlepath")
            }
            .tag(1)

            // BLE Log Tab
            NavigationView {
                ZStack {
                    Color(red: 0.04, green: 0.05, blue: 0.07).ignoresSafeArea()
                    BleLogView(ble: ble)
                }
                .navigationTitle("BLE Logs")
            }
            .tabItem {
                Label("Logs", systemImage: "terminal.fill")
            }
            .tag(2)
        }
        .accentColor(Color(red: 0.0, green: 0.9, blue: 1.0))
        .sheet(item: $editingCard) { card in
            CardEditSheet(card: card) { updated in
                if let idx = cards.firstIndex(where: { $0.id == updated.id }) {
                    cards[idx] = updated
                } else {
                    cards.append(updated)
                }
            }
        }
    }

    private var storageBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Device Storage Estimate")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                Spacer()
                Text("\(cards.count) cards • Est: ~\(cards.count * 80) B / 1 MB")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            ProgressView(value: Double(cards.count * 80), total: 1_048_576)
                .accentColor(Color(red: 0.0, green: 0.9, blue: 1.0))
        }
        .padding(12)
        .background(Color(red: 0.08, green: 0.10, blue: 0.13))
        .cornerRadius(8)
        .padding(.horizontal)
        .padding(.top, 8)
    }
}

// Model for iOS Card
struct IOSCard: Identifiable {
    let id: Int
    var frontText: String
    var backText: String
    var frontImgId: UInt32
    var backImgId: UInt32
}

struct CardRowView: View {
    let card: IOSCard
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(card.frontText.isEmpty ? "(image-only)" : card.frontText)
                .font(.headline)
                .foregroundColor(.white)
            Text(card.backText.isEmpty ? "(image-only)" : card.backText)
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .padding(.vertical, 4)
    }
}

struct CardEditSheet: View {
    @Environment(\.presentationMode) var presentationMode
    @State var card: IOSCard
    let onSave: (IOSCard) -> Void

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Front (Max 100 chars, ASCII)")) {
                    TextField("Front prompt", text: $card.frontText)
                        .onChange(of: card.frontText) { val in
                            if val.count > 100 { card.frontText = String(val.prefix(100)) }
                        }
                    Text("\(card.frontText.count)/100")
                        .font(.caption)
                        .foregroundColor(.gray)
                }

                Section(header: Text("Back (Max 100 chars, ASCII)")) {
                    TextField("Back details", text: $card.backText)
                        .onChange(of: card.backText) { val in
                            if val.count > 100 { card.backText = String(val.prefix(100)) }
                        }
                    Text("\(card.backText.count)/100")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
            .navigationTitle("Edit Flashcard")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(card)
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                }
            }
        }
    }
}

struct SyncView: View {
    @ObservedObject var ble: IOSBleManager
    let cards: [IOSCard]

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Image(systemName: ble.isConnected ? "checkmark.circle.fill" : "antenna.radiowaves.left.and.right")
                    .font(.system(size: 44))
                    .foregroundColor(ble.isConnected ? .green : Color(red: 0.0, green: 0.9, blue: 1.0))
                Text(ble.connectionStateText)
                    .font(.headline)
                    .foregroundColor(.white)
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color(red: 0.08, green: 0.10, blue: 0.13))
            .cornerRadius(12)
            .padding(.horizontal)

            if !ble.isConnected {
                Button(action: { ble.startScan() }) {
                    Text(ble.isScanning ? "Scanning..." : "Scan for Ollo")
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.0, green: 0.9, blue: 1.0))
                        .foregroundColor(Color(red: 0.0, green: 0.2, blue: 0.25))
                        .cornerRadius(10)
                }
                .padding(.horizontal)
            } else {
                Button(action: { ble.startSync(cards: cards) }) {
                    Text("Sync \(cards.count) Cards to Glasses")
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.0, green: 0.9, blue: 1.0))
                        .foregroundColor(Color(red: 0.0, green: 0.2, blue: 0.25))
                        .cornerRadius(10)
                }
                .padding(.horizontal)
            }

            if ble.isSyncing {
                VStack(spacing: 6) {
                    ProgressView(value: ble.syncProgress)
                        .accentColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                    Text(ble.syncStepText)
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .padding(.horizontal)
            }

            Spacer()
        }
        .padding(.top)
    }
}

struct BleLogView: View {
    @ObservedObject var ble: IOSBleManager

    var body: some View {
        List(ble.packetLogs.reversed()) { log in
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(log.direction)
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(log.direction == "TX" ? Color(red: 0.0, green: 0.9, blue: 1.0) : .green)
                    Text(log.typeName)
                        .font(.caption)
                        .foregroundColor(.white)
                    Spacer()
                    Text(log.timestamp)
                        .font(.caption2)
                        .foregroundColor(.gray)
                }
                Text(log.hex)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
            }
            .listRowBackground(Color(red: 0.08, green: 0.10, blue: 0.13))
        }
        .listStyle(.plain)
    }
}

// iOS Native CoreBluetooth implementation matching exact protocol
class IOSBleManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    @Published var isConnected = false
    @Published var isScanning = false
    @Published var isSyncing = false
    @Published var syncProgress: Double = 0.0
    @Published var syncStepText = ""
    @Published var connectionStateText = "Disconnected"
    @Published var packetLogs: [IOSPacketLog] = []

    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var writeChar: CBCharacteristic?
    private var notifyChar: CBCharacteristic?

    let serviceUUID = CBUUID(string: "6f6c6c6f-0001-4000-8000-00805f9b34fb")
    let writeUUID = CBUUID(string: "6f6c6c6f-0002-4000-8000-00805f9b34fb")
    let notifyUUID = CBUUID(string: "6f6c6c6f-0003-4000-8000-00805f9b34fb")

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            connectionStateText = "Ready to scan"
        } else {
            connectionStateText = "Bluetooth off"
        }
    }

    func startScan() {
        isScanning = true
        connectionStateText = "Scanning for Ollo..."
        centralManager.scanForPeripherals(withServices: [serviceUUID], options: nil)
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        self.peripheral = peripheral
        centralManager.stopScan()
        isScanning = false
        connectionStateText = "Connecting..."
        centralManager.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnected = true
        connectionStateText = "Connected to \(peripheral.name ?? "Ollo")"
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        isConnected = false
        connectionStateText = "Disconnected"
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == serviceUUID {
            peripheral.discoverCharacteristics([writeUUID, notifyUUID], for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let chars = service.characteristics else { return }
        for char in chars {
            if char.uuid == writeUUID { writeChar = char }
            if char.uuid == notifyUUID {
                notifyChar = char
                peripheral.setNotifyValue(true, for: char)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        logPacket(direction: "RX", type: "NOTIFY", data: data)
    }

    func startSync(cards: [IOSCard]) {
        guard let peripheral = peripheral, let writeChar = writeChar else { return }
        isSyncing = true
        syncStepText = "Sending BEGIN_SYNC..."
        syncProgress = 0.1

        // Send BEGIN_SYNC (0x01, u16 count)
        var count = UInt16(cards.count).littleEndian
        var beginData = Data([0x01])
        beginData.append(Data(bytes: &count, count: 2))
        peripheral.writeValue(beginData, for: writeChar, type: .withoutResponse)
        logPacket(direction: "TX", type: "BEGIN_SYNC", data: beginData)

        // Stream cards
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            for (idx, card) in cards.enumerated() {
                var index = UInt16(idx).littleEndian
                var fId = card.frontImgId.littleEndian
                var bId = card.backImgId.littleEndian
                let fBytes = card.frontText.data(using: .ascii) ?? Data()
                let bBytes = card.backText.data(using: .ascii) ?? Data()
                var fLen = UInt8(fBytes.count)
                var bLen = UInt8(bBytes.count)

                var cardPacket = Data([0x02])
                cardPacket.append(Data(bytes: &index, count: 2))
                cardPacket.append(Data(bytes: &fId, count: 4))
                cardPacket.append(Data(bytes: &bId, count: 4))
                cardPacket.append(fLen)
                cardPacket.append(bLen)
                cardPacket.append(fBytes)
                cardPacket.append(bBytes)

                peripheral.writeValue(cardPacket, for: writeChar, type: .withoutResponse)
                self.logPacket(direction: "TX", type: "CARD #\(idx)", data: cardPacket)
            }

            // Send END_SYNC (0x06)
            let endData = Data([0x06])
            peripheral.writeValue(endData, for: writeChar, type: .withoutResponse)
            self.logPacket(direction: "TX", type: "END_SYNC", data: endData)

            self.syncProgress = 1.0
            self.syncStepText = "Sync finished!"
            self.isSyncing = false
        }
    }

    func logPacket(direction: String, type: String, data: Data) {
        let hex = data.map { String(format: "%02X ", $0) }.joined()
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        let entry = IOSPacketLog(id: UUID(), timestamp: formatter.string(from: Date()), direction: direction, typeName: type, hex: hex)
        DispatchQueue.main.async {
            self.packetLogs.append(entry)
            if self.packetLogs.count > 100 { self.packetLogs.removeFirst() }
        }
    }
}

struct IOSPacketLog: Identifiable {
    let id: UUID
    let timestamp: String
    let direction: String
    let typeName: String
    let hex: String
}
