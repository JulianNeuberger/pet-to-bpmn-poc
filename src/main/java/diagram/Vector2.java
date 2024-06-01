package diagram;

public class Vector2 {
    private final double x;
    private final double y;

    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Vector2 mul(double scalar) {
        return new Vector2(
                this.x * scalar,
                this.y * scalar
        );
    }

    public Vector2 sub(Vector2 other) {
        return new Vector2(
                this.x - other.x,
                this.y - other.y
        );
    }

    public Vector2 sub(double scalar) {
        return new Vector2(
                this.x - scalar,
                this.y - scalar
        );
    }
}
