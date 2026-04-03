import SwiftUI

// MARK: - MovieCardView
/// Grid card for a movie with poster, rating, and watched indicator
struct MovieCardView: View {
    let item: MediaItem
    @State private var isPressed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .topTrailing) {
                MediaPosterView(item: item, width: 110, height: 160)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.orange.opacity(0.3), lineWidth: 1)
                    )

                // Watched badge
                if item.isWatched {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .background(Color.black.opacity(0.7).clipShape(Circle()))
                        .padding(4)
                }

                // In-progress badge
                if !item.isWatched && item.progressPercentage > 0.01 {
                    VStack {
                        Spacer()
                        ProgressView(value: item.progressPercentage)
                            .tint(.orange)
                            .padding(.horizontal, 4)
                            .padding(.bottom, 4)
                    }
                    .frame(width: 110, height: 160)
                }
            }

            Text(item.title)
                .font(.caption2)
                .foregroundColor(.white)
                .lineLimit(2)
                .frame(width: 110, alignment: .leading)

            if let year = item.releaseYear {
                Text(String(year))
                    .font(.system(size: 10))
                    .foregroundColor(.gray)
            }
        }
        .scaleEffect(isPressed ? 0.95 : 1.0)
        .animation(.spring(response: 0.2), value: isPressed)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
    }
}
