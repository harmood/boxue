import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

void main() {
  runApp(const ProviderScope(child: BoxueApp()));
}

final _router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const HomePage(),
    ),
  ],
);

class BoxueApp extends StatelessWidget {
  const BoxueApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: '博学',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF409EFF)),
        useMaterial3: true,
      ),
      routerConfig: _router,
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('博学')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            '把知识变成自己的题库',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text('Android 客户端骨架已经建立，下一步接入登录、题库和离线同步。'),
          const SizedBox(height: 24),
          const _MetricCard(label: '题库', value: '0'),
          const _MetricCard(label: '今日已练', value: '0'),
          const _MetricCard(label: '待复习', value: '0'),
        ],
      ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        title: Text(label),
        trailing: Text(
          value,
          style: Theme.of(context).textTheme.titleLarge,
        ),
      ),
    );
  }
}
