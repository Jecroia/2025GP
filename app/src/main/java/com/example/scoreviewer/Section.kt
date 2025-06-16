// Section.kt

import android.os.Parcel
import android.os.Parcelable

data class Section(
    val name: String,
    val startPage: Int,
    val endPage: Int
) : Parcelable {
    // 1. describeContents: 대부분 0을 반환합니다.
    override fun describeContents(): Int = 0

    // 2. writeToParcel: 필드를 반드시 같은 순서로 기록
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeInt(startPage)
        dest.writeInt(endPage)
    }

    companion object {
        // 3. CREATOR: Parcel → 객체로 복원
        @JvmField
        val CREATOR: Parcelable.Creator<Section> = object : Parcelable.Creator<Section> {
            override fun createFromParcel(source: Parcel): Section {
                val name = source.readString() ?: ""
                val startPage = source.readInt()
                val endPage = source.readInt()
                return Section(name, startPage, endPage)
            }
            override fun newArray(size: Int): Array<Section?> =
                arrayOfNulls(size)
        }
    }
}
