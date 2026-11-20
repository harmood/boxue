import 'package:boxue_android/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders the home page', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: BoxueApp()));
    expect(find.text('博学'), findsWidgets);
  });
}