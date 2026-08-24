package com.example.productionmvp;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    private final SectionRepository sectionRepository;
    private final PostRepository postRepository;
    private final WorkerRepository workerRepository;
    private final MaterialRepository materialRepository;
    private final ProductModelRepository productModelRepository;
    private final AssemblyRepository assemblyRepository;
    private final OperationRepository operationRepository;
    private final SeriesRepository seriesRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final com.example.productionmvp.config.DeploymentMode deploymentMode;
    private final boolean seedDemoData;
    private final String bootstrapAdminPin;

    public DataSeeder(SectionRepository sectionRepository,
                      PostRepository postRepository,
                      WorkerRepository workerRepository,
                      MaterialRepository materialRepository,
                      ProductModelRepository productModelRepository,
                      AssemblyRepository assemblyRepository,
                      OperationRepository operationRepository,
                      SeriesRepository seriesRepository,
                      ProductInstanceRepository productInstanceRepository,
                      AssemblyInstanceRepository assemblyInstanceRepository,
                      com.example.productionmvp.config.DeploymentMode deploymentMode,
                      @org.springframework.beans.factory.annotation.Value("${app.seed-demo-data:false}")
                      boolean seedDemoData,
                      @org.springframework.beans.factory.annotation.Value("${app.bootstrap-admin-pin:}")
                      String bootstrapAdminPin) {
        this.deploymentMode = deploymentMode;
        this.seedDemoData = seedDemoData;
        this.bootstrapAdminPin = bootstrapAdminPin;
        this.sectionRepository = sectionRepository;
        this.postRepository = postRepository;
        this.workerRepository = workerRepository;
        this.materialRepository = materialRepository;
        this.productModelRepository = productModelRepository;
        this.assemblyRepository = assemblyRepository;
        this.operationRepository = operationRepository;
        this.seriesRepository = seriesRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
    }

    // Without the demo accounts a fresh production database has nobody in it, and there is no
    // way to make the first account: creating a worker requires a manager or admin to already
    // be signed in. That is a deployment that cannot be logged into at all. This creates the
    // one account needed to get in and set everything else up through the UI, from a PIN the
    // operator chooses, and only while the database is genuinely empty.
    private void createBootstrapAdmin() {
        if (workerRepository.count() > 0) {
            return;
        }
        if (bootstrapAdminPin == null || bootstrapAdminPin.isBlank()) {
            System.out.println("=================================================================");
            System.out.println("This database has no accounts and none can be created without one.");
            System.out.println("Restart once with APP_BOOTSTRAP_ADMIN_PIN set to a PIN of your");
            System.out.println("choice to create the first administrator, then sign in and add");
            System.out.println("the real accounts through the UI.");
            System.out.println("=================================================================");
            return;
        }

        Worker admin = new Worker();
        admin.setName("Адміністратор");
        admin.setRole("Адміністратор");
        admin.setPosition("Системний адміністратор");
        admin.setSystemRole(SystemRole.ADMIN);
        admin.setPinHash(new BCryptPasswordEncoder().encode(bootstrapAdminPin.trim()));
        workerRepository.save(admin);

        System.out.println("Created the initial administrator account from APP_BOOTSTRAP_ADMIN_PIN. "
                + "Change that PIN after signing in, and remove the variable from the environment.");
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // These are demo accounts with PINs written down in this file and in the handover notes
        // (7777 for a manager, 9999 for an admin). On the embedded database they vanish on the
        // next restart. On a real database they would be permanent, and since the seeder only
        // runs once against an empty schema, nobody would see it happen or think to remove
        // them. Seeding is therefore opt-in for a persistent deployment.
        if (deploymentMode.isPersistentDeployment() && !seedDemoData) {
            System.out.println("Persistent database detected - skipping demo data. "
                    + "Set APP_SEED_DEMO_DATA=true to seed it anyway (demo/staging only).");
            createBootstrapAdmin();
            return;
        }

        if (sectionRepository.count() > 0) {
            System.out.println("Data already seeded. Skipping DataSeeder.");
            return;
        }

        System.out.println("Starting data seeding...");

        // 1. Sections and Posts
        Section section1 = new Section();
        section1.setName("Металообробка");
        section1.setLocation("Цех 1");
        section1 = sectionRepository.save(section1);

        Post post1 = new Post();
        post1.setName("Порізка-01");
        post1.setMaxCapacity(2);
        post1.setSection(section1);
        post1 = postRepository.save(post1);

        Post post2 = new Post();
        post2.setName("Гнуття-01");
        post2.setMaxCapacity(2);
        post2.setSection(section1);
        post2 = postRepository.save(post2);

        Post post3 = new Post();
        post3.setName("Свердління-01");
        post3.setMaxCapacity(3);
        post3.setSection(section1);
        post3 = postRepository.save(post3);

        Section section2 = new Section();
        section2.setName("Зварювання");
        section2.setLocation("Цех 1");
        section2 = sectionRepository.save(section2);

        Post post4 = new Post();
        post4.setName("Зварювання-01");
        post4.setMaxCapacity(2);
        post4.setSection(section2);
        post4 = postRepository.save(post4);

        Section section3 = new Section();
        section3.setName("Покриття");
        section3.setLocation("Цех 2");
        section3 = sectionRepository.save(section3);

        Post post5 = new Post();
        post5.setName("Малярка-01");
        post5.setMaxCapacity(4);
        post5.setSection(section3);
        post5 = postRepository.save(post5);
        
        System.out.println("Created sections and posts.");

        // 2. Workers
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        Worker worker1 = new Worker();
        worker1.setName("Іван");
        worker1.setRole("Порізник");
        worker1.setPosition("Оператор порізки");
        worker1.setSystemRole(SystemRole.WORKER);
        worker1.setSection(section1);
        worker1.setPinHash(encoder.encode("1234"));
        workerRepository.save(worker1);

        Worker worker2 = new Worker();
        worker2.setName("Петро");
        worker2.setRole("Зварювальник");
        worker2.setPosition("Оператор зварювання");
        worker2.setSystemRole(SystemRole.WORKER);
        worker2.setSection(section2);
        worker2.setPinHash(encoder.encode("2345"));
        workerRepository.save(worker2);

        Worker worker3 = new Worker();
        worker3.setName("Микола");
        worker3.setRole("Слюсар");
        worker3.setPosition("Слюсар");
        worker3.setSystemRole(SystemRole.WORKER);
        worker3.setSection(section1);
        worker3.setPinHash(encoder.encode("3456"));
        workerRepository.save(worker3);

        Worker worker4 = new Worker();
        worker4.setName("Олег");
        worker4.setRole("Маляр");
        worker4.setPosition("Оператор покриття");
        worker4.setSystemRole(SystemRole.WORKER);
        worker4.setSection(section3);
        worker4.setPinHash(encoder.encode("4567"));
        workerRepository.save(worker4);

        Worker worker5 = new Worker();
        worker5.setName("Марія");
        worker5.setRole("Диспетчер");
        worker5.setPosition("Диспетчер виробництва");
        worker5.setSystemRole(SystemRole.DISPATCHER);
        worker5.setPinHash(encoder.encode("5678"));
        workerRepository.save(worker5);

        Worker manager = new Worker();
        manager.setName("Андрій");
        manager.setRole("Менеджер");
        manager.setPosition("Менеджер виробництва");
        manager.setSystemRole(SystemRole.MANAGER);
        manager.setPinHash(encoder.encode("7777"));
        workerRepository.save(manager);

        Worker admin = new Worker();
        admin.setName("Адмін");
        admin.setRole("Адміністратор");
        admin.setPosition("Системний адміністратор");
        admin.setSystemRole(SystemRole.ADMIN);
        admin.setPinHash(encoder.encode("9999"));
        workerRepository.save(admin);

        // Dedicated low-privilege account for the shop-floor TV display - its own PIN, not
        // whoever happens to set the screen up borrowing their personal one. See SystemRole.TV.
        Worker tvDisplay = new Worker();
        tvDisplay.setName("ТВ-Дисплей");
        tvDisplay.setRole("ТВ");
        tvDisplay.setPosition("Виробничий дисплей цеху");
        tvDisplay.setSystemRole(SystemRole.TV);
        tvDisplay.setPinHash(encoder.encode("0000"));
        workerRepository.save(tvDisplay);

        System.out.println("Created workers.");

        // 3. Materials
        Material mat1 = new Material();
        mat1.setName("Метал 3мм");
        mat1.setUnit("кг");
        mat1.setNormPerUnit(2.0);
        mat1.setAvailableStock(100.0);
        mat1.setMinimumStock(20.0);
        mat1.setSupplier("МеталСервіс");
        mat1 = materialRepository.save(mat1);

        Material mat2 = new Material();
        mat2.setName("Метал 1.5мм");
        mat2.setUnit("кг");
        mat2.setNormPerUnit(0.5);
        mat2.setAvailableStock(50.0);
        mat2.setMinimumStock(10.0);
        mat2.setSupplier("МеталСервіс");
        mat2 = materialRepository.save(mat2);

        Material mat3 = new Material();
        mat3.setName("Гвинти М6");
        mat3.setUnit("шт");
        mat3.setNormPerUnit(4.0);
        mat3.setAvailableStock(500.0);
        mat3.setMinimumStock(50.0);
        mat3.setSupplier("Фастен-Про");
        mat3 = materialRepository.save(mat3);

        Material mat4 = new Material();
        mat4.setName("Фарба чорна");
        mat4.setUnit("л");
        mat4.setNormPerUnit(0.1);
        mat4.setAvailableStock(10.0);
        mat4.setMinimumStock(2.0);
        mat4.setSupplier("ФарбПром");
        mat4 = materialRepository.save(mat4);

        System.out.println("Created materials.");

        // 4. Product Model
        ProductModel model = new ProductModel();
        model.setName("Тестовий блок");
        model.setVersion("1.0");
        model = productModelRepository.save(model);
        
        System.out.println("Created product model.");

        // 5. Assemblies
        Assembly asm1 = new Assembly();
        asm1.setName("Основа");
        asm1.setCode("ОСН-А");
        asm1.setCategory("Корпус");
        asm1.setProductModel(model);
        asm1 = assemblyRepository.save(asm1);

        Assembly asm2 = new Assembly();
        asm2.setName("Кришка");
        asm2.setCode("КРШ-Б");
        asm2.setCategory("Корпус");
        asm2.setProductModel(model);
        asm2 = assemblyRepository.save(asm2);

        Assembly asm3 = new Assembly();
        asm3.setName("Кріплення");
        asm3.setCode("КРП-В");
        asm3.setCategory("Кріплення");
        asm3.setProductModel(model);
        asm3 = assemblyRepository.save(asm3);

        System.out.println("Created assemblies.");

        // 6. Operations
        Operation op1 = new Operation();
        op1.setName("Порізка");
        op1.setNormativeTimeMinutes(30);
        op1.setSection(section1);
        op1.setPost(post1);
        op1.setOrderIndex(1);
        op1.setType(OperationType.INDIVIDUAL);
        op1.setAssembly(asm1);
        op1.setMaterialQuantityPerUnit(2.0);
        Set<Material> ops1mats = new HashSet<>();
        ops1mats.add(mat1);
        op1.setRequiredMaterials(ops1mats);
        op1 = operationRepository.save(op1);

        Operation op2 = new Operation();
        op2.setName("Зварювання");
        op2.setNormativeTimeMinutes(60);
        op2.setSection(section2);
        op2.setPost(post4);
        op2.setOrderIndex(2);
        op2.setType(OperationType.INDIVIDUAL);
        op2.setAssembly(asm1);
        op2 = operationRepository.save(op2);

        // Assembly A Dependency
        op2.setDependsOnOperation(op1);
        operationRepository.save(op2);

        Operation op3 = new Operation();
        op3.setName("Гнуття");
        op3.setNormativeTimeMinutes(20);
        op3.setSection(section1);
        op3.setPost(post2);
        op3.setOrderIndex(1);
        op3.setType(OperationType.INDIVIDUAL);
        op3.setAssembly(asm2);
        op3.setMaterialQuantityPerUnit(0.5);
        Set<Material> ops3mats = new HashSet<>();
        ops3mats.add(mat2);
        op3.setRequiredMaterials(ops3mats);
        op3 = operationRepository.save(op3);

        Operation op4 = new Operation();
        op4.setName("Покриття");
        op4.setNormativeTimeMinutes(45);
        op4.setSection(section3);
        op4.setPost(post5);
        op4.setOrderIndex(2);
        op4.setType(OperationType.INDIVIDUAL);
        op4.setAssembly(asm2);
        op4.setMaterialQuantityPerUnit(0.1);
        Set<Material> ops4mats = new HashSet<>();
        ops4mats.add(mat4);
        op4.setRequiredMaterials(ops4mats);
        op4 = operationRepository.save(op4);

        // Assembly B Dependency
        op4.setDependsOnOperation(op3);
        operationRepository.save(op4);

        Operation op5 = new Operation();
        op5.setName("Свердління");
        op5.setNormativeTimeMinutes(15);
        op5.setSection(section1);
        op5.setPost(post3);
        op5.setOrderIndex(1);
        op5.setType(OperationType.INDIVIDUAL);
        op5.setAssembly(asm3);
        op5.setMaterialQuantityPerUnit(4.0);
        Set<Material> ops5mats = new HashSet<>();
        ops5mats.add(mat3);
        op5.setRequiredMaterials(ops5mats);
        op5 = operationRepository.save(op5);

        System.out.println("Created operations.");
        System.out.println("DataSeeder completed successfully.");
    }
}
